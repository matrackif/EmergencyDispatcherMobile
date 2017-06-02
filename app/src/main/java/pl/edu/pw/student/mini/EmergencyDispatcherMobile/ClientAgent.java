
package pl.edu.pw.student.mini.EmergencyDispatcherMobile;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import chat.ontology.ChatOntology;
import chat.ontology.Joined;
import chat.ontology.Left;
import jade.content.ContentManager;
import jade.content.Predicate;
import jade.content.lang.Codec;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.util.Logger;
import jade.util.leap.Iterator;
import jade.util.leap.Set;
import jade.util.leap.SortedSetImpl;

public class ClientAgent extends Agent implements ClientInterface {
	private static final long serialVersionUID = 1594371294421614291L;

	private Logger logger = Logger.getJADELogger(this.getClass().getName());

	private static final String CHAT_ID = "__chat__";
	private static final String CHAT_MANAGER_NAME = "manager";
	private String type = MainActivity.getType();//static field from the main activity

	private Set participants = new SortedSetImpl();
	private java.util.HashMap<AID,String> participantAgents = new java.util.HashMap<>();
	private Codec codec = new SLCodec();
	private Ontology onto = ChatOntology.getInstance();
	private ACLMessage spokenMsg;

	private Context context;

	protected void setup() {
		Object[] args = getArguments();
		if (args != null && args.length > 0) {
			if (args[0] instanceof Context) {
				context = (Context) args[0];
			}
		}

		// Register language and ontology
		ContentManager cm = getContentManager();
		cm.registerLanguage(codec);
		cm.registerOntology(onto);
		cm.setValidationMode(false);

		// Add initial behaviours
		addBehaviour(new ParticipantsManager(this));
		addBehaviour(new RequestListener(this));

		// Initialize the message used to convey spoken sentences
		spokenMsg = new ACLMessage(ACLMessage.INFORM);
		spokenMsg.setConversationId(CHAT_ID);

		// Activate the GUI
		registerO2AInterface(ClientInterface.class, this);
		setType(type);
		Intent broadcast = new Intent();
		broadcast.setAction("jade.demo.user_dispatcher.SHOW_DISPATCHER");
		logger.log(Level.INFO, "Sending broadcast " + broadcast.getAction());
		context.sendBroadcast(broadcast);
	}

	protected void takeDown() {

	}

	private void notifyParticipantsChanged() {
		Intent broadcast = new Intent();
		broadcast.setAction("jade.demo.user_dispatcher.REFRESH_PARTICIPANTS");
		logger.log(Level.INFO, "Sending broadcast " + broadcast.getAction());
		context.sendBroadcast(broadcast);
	}

	private void handleReceivedMessage(String speaker, String sentence, String action) {
		/* This broadcasts the received message to all "broadcast receivers" which is basically only ourself
		 * It allows as to communicate with the main activity. (See the MyReceiver class in MainActivity.java)
		 * Based on the "action" argument we decide on what we send to the main activity
		 */
		Intent broadcast = new Intent();
		broadcast.setAction(action);
		broadcast.putExtra("sentence", speaker + ": " + sentence + "\n");
		logger.log(Level.INFO, "Sending broadcast " + broadcast.getAction() + " Sentence: " + broadcast.getStringExtra("sentence"));
		context.sendBroadcast(broadcast);
	}

	/**
	 * Inner class ParticipantsManager. This behaviour registers as a user_dispatcher
	 * participant and keeps the list of participants up to date by managing the
	 * information received from the ChatManager agent.
	 */
	class ParticipantsManager extends CyclicBehaviour {
		private static final long serialVersionUID = -4845730529175649756L;
		private MessageTemplate template;

		ParticipantsManager(Agent a) {
			super(a);
		}

		public void onStart() {
			// Subscribe as a user_dispatcher participant to the ChatManager agent
			ACLMessage subscription = new ACLMessage(ACLMessage.SUBSCRIBE);
			subscription.setLanguage(codec.getName());
			subscription.setOntology(onto.getName());
			String convId = "C-" + myAgent.getLocalName();
			subscription.setContent(type);
			subscription.setConversationId(convId);
			subscription
					.addReceiver(new AID(CHAT_MANAGER_NAME, AID.ISLOCALNAME));
			myAgent.send(subscription);
			// Initialize the template used to receive notifications
			// from the ChatManagerAgent
			template = MessageTemplate.MatchConversationId(convId);
		}

		public void action() {
			// Receives information about people joining and leaving
			// the user_dispatcher from the ChatManager agent
			ACLMessage msg = myAgent.receive(template);
			if (msg != null) {
				if (msg.getPerformative() == ACLMessage.INFORM) {
					try {
						Predicate p = (Predicate) myAgent.getContentManager().extractContent(msg);
						if(p instanceof Joined) {
							Joined joined = (Joined) p;
							List<AID> aid = (List<AID>) joined.getWho();
							for(AID a : aid)
							{
								String[] codedMsg = a.getName().split("_");
								String agentName = codedMsg[0];
								String agentType = codedMsg[1];
								logger.log(Logger.INFO,agentName + agentType);
								a.setName(agentName);
								participantAgents.put(a,agentType);
								participants.add(a);
							}

							notifyParticipantsChanged();
						}
						if(p instanceof Left) {
							Left left = (Left) p;
							List<AID> aid = (List<AID>) left.getWho();
							for(AID a : aid)
							{
								participants.remove(a);
								participantAgents.remove(a);
							}

							notifyParticipantsChanged();
						}
					} catch (Exception e) {
						Logger.println(e.toString());
						e.printStackTrace();
					}
				} else {
					handleUnexpected(msg);
				}
			} else {
				block();
			}
		}
	} // END of inner class ParticipantsManager

	/**
	 * Inner class RequestListener. This behaviour registers as a user_dispatcher participant
	 * and keeps the list of participants up to date by managing the information
	 * received from the ChatManager agent.
	 */
	class RequestListener extends CyclicBehaviour {
		private static final long serialVersionUID = 741233963737842521L;
		private MessageTemplate template = MessageTemplate
				.MatchConversationId(CHAT_ID);

		RequestListener(Agent a) {
			super(a);
		}

		public void action() {
			ACLMessage msg = myAgent.receive(template);
			if (msg != null) {
				//Here we check what message we received, and based on that we call the proper "notify" method
				Log.i("RequestListener", "Received messaged with content: " + msg.getContent() + " from: " + msg.getSender());
				if (msg.getPerformative() == ACLMessage.INFORM) {
					//The message performative is INFORM, so it means that the agent wants to inform us about their latitude and longitude
					//TODO maybe find a different way of distinguishing messages because maybe we want to INFORM about other things besides LatLng?
					//At the moment "spokenMsg" is always an "INFORM" message, and that's what we send in handleSpoken
					//IMPORTANT: EVERY TIME WE ADD A NEW ACTION WE HAVE TO REGISTER IT WITH THE BROADCAST RECEIVER IN MainActivity.java
					handleReceivedMessage(msg.getSender().getLocalName(), msg.getContent(), MainActivity.ACTION_SEND_LAT_LONG);
				}
				else if(msg.getPerformative() == ACLMessage.REQUEST){
					// A user is requesting help
					handleReceivedMessage(msg.getSender().getLocalName(), msg.getContent(), PoliceDispatcherActivity.ACTION_REQUEST_HELP);
				}
				else {
					handleUnexpected(msg);
				}
			}

			else {
				block();
			}
		}
	} // END of inner class RequestListener

	/**
	 * Inner class ChatSpeaker. INFORMs other participants about a spoken
	 * sentence
	 */
	private class ChatSpeaker extends OneShotBehaviour {
		private static final long serialVersionUID = -1426033904935339194L;
		private String sentence;
		private MessageTemplate template;
		private int performative;
		private ChatSpeaker(Agent a, String s) {
			super(a);
			sentence = s;
		}

		public void action() {
			spokenMsg.clearAllReceiver();
			Iterator it = participants.iterator();
			while (it.hasNext()) {
				spokenMsg.addReceiver((AID) it.next());
			}
			spokenMsg.setContent(sentence);
			if(sentence.equalsIgnoreCase(UserDispatcherActivity.HELP_MSG)){
				//Help message should be a REQUEST perforamtive (like request help)
				spokenMsg.setPerformative(ACLMessage.REQUEST);
			}
			// I commented this line below because we didn't need it, the MainActivity didn't even handle it
			//handleReceivedMessage(myAgent.getLocalName(), sentence, null);
			send(spokenMsg);
			spokenMsg.setPerformative(ACLMessage.INFORM);
			//After sending message set performative back to INFORM so we can send lat/longs again
		}
	} // END of inner class ChatSpeaker
	private class ChatSpecificSpeaker extends OneShotBehaviour {
		private static final long serialVersionUID = -142323904935339194L;
		private String sentence;
		private AID recv;
		private int performative;
		private ChatSpecificSpeaker(Agent a, String s, AID aid) {
			super(a);
			sentence = s;
			recv = aid;
		}

		public void action() {
			spokenMsg.clearAllReceiver();
			spokenMsg.addReceiver(recv);
			spokenMsg.setContent(sentence);
			if(sentence.equalsIgnoreCase(UserDispatcherActivity.HELP_MSG)){
				//Help message should be a REQUEST perforamtive (like request help)
				spokenMsg.setPerformative(ACLMessage.REQUEST);
			}
			// I commented this line below because we didn't need it, the MainActivity didn't even handle it
			//handleReceivedMessage(myAgent.getLocalName(), sentence, null);
			send(spokenMsg);
			//After sending message set performative back to INFORM so we can send lat/longs again
			spokenMsg.setPerformative(ACLMessage.INFORM);

		}
	}

	@Override
	public void setType(String s) {
		type = s;
	}

	@Override
	public String getType() {
		return type;
	}

	// ///////////////////////////////////////
	// Methods called by the interface
	// ///////////////////////////////////////
	public void handleSpoken(String s) {
		// Add a ChatSpeaker behaviour that INFORMs all participants about
		// the spoken sentence
		addBehaviour(new ChatSpeaker(this, s));
	}


	public void handleSpoken(String s, AID a) {
		addBehaviour(new ChatSpecificSpeaker(this,s,a));
	}

		public String[] getParticipantNames() {

		String[] pp = new String[participantAgents.size()];
		java.util.Iterator it = participantAgents.entrySet().iterator();
		int i =0;
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry)it.next();
			AID currentAID = (AID) pair.getKey();
			pp[i++] = currentAID.getLocalName()+"_"+pair.getValue()+"_"+currentAID;
			logger.log(Logger.INFO, "Content is: " + pair.getKey() + "("+pair.getValue()+")");
		}
		return pp;

	}

	// //////////////////////////////////////
	// Private utility method
	// ///////////////////////////////////////
	private void handleUnexpected(ACLMessage msg) {
		if (logger.isLoggable(Logger.WARNING)) {
			logger.log(Logger.WARNING, "Unexpected message received from "
					+ msg.getSender().getName());
			logger.log(Logger.WARNING, "Content is: " + msg.getContent());
		}
	}

}
