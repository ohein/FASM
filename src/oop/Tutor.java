package test;

public class Tutor extends Student implements Lehrkraft {
	float verg�tung = 0;
	public Tutor() { }
	public Tutor( String name ) { super( name ); }
	public void �bernehmeVorlesung(String vorlesung, int stunden) {
		System.out.println( "Tutor " + name + " �bernimmt " + vorlesung);
		verg�tung += stunden * 10;
		System.out.println( "Verg�tung: " + verg�tung + " Euro");
	}
}
