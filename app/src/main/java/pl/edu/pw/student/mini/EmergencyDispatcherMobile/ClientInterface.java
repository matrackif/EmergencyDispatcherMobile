package pl.edu.pw.student.mini.EmergencyDispatcherMobile;


import jade.core.AID;

public interface ClientInterface {

	public void handleSpoken(String s);
	public void handleSpoken(String s , AID a);
	public String[] getParticipantNames();

}