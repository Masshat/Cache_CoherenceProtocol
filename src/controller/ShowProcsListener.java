package controller;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * 
 * @author QLM
 * This class implements the listener associated to the "Show Procs" button.
 */
public class ShowProcsListener implements ActionListener {

	private Controlleur controlleur;
	
	public ShowProcsListener(Controlleur c) {
		controlleur = c;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		controlleur.getVue().switchShowProcs();
		controlleur.repaint();
	}
	
}
