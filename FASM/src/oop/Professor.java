package test;

public class Professor extends Person implements Lehrkraft {
	private int SWS = 0;
	public Professor() {}
	public Professor(String name) { super( name ); }
	public void �bernehmeVorlesung(String vorlesung, int stunden) {
		System.out.println( "Prof. " + name + " �bernimmt " + vorlesung);
		SWS += stunden;
		System.out.println( "SWS: " + SWS);
	}
}