package view;

import java.awt.Dimension;

import javax.swing.JFrame;


import controller.Controlleur;

/**
 * Derived from JFrame, this class is the top-level class in the View package
 * It contains the main JPanel (Panneau) and is linked to the controlleur
 * @author QLM
 *
 */
public class Vue extends JFrame {

	private static final long serialVersionUID = 1L;

	private Panneau panneau;

	public Vue(Dimension dim) {
		panneau = new Panneau(dim);

		this.setTitle("Simulation :: Coherence de cache");
		this.setResizable(true);
		this.setSize(panneau.getPreferredSize());
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setLocationRelativeTo(null);

		this.setContentPane(panneau);
		this.setExtendedState(MAXIMIZED_BOTH);
	}

	public void setControlleur(Controlleur c) {
		panneau.setControlleur(c);
	}
	
	public Panneau getPanneau() {
		return panneau;
	}
	
	public void switchShowProcs() {
		panneau.switchShowProcs();
	}
		
	
	
}
