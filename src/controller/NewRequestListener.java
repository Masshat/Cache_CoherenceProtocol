package controller;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import view.NewRequestDialog;

/**
 * 
 * This class implements the actions taken when we click on the "New Request" button
 * Basically, the Dialog (pop-up) is created once in the constructor, and then only
 * set visible when we click.
 * It also serves to call back methods of the controlleur to transmit requests from the 
 * NewRequestDialog objects to the model.
 * @author QLM
 *  
 */
public class NewRequestListener implements ActionListener {

	private Controlleur controlleur;
	private NewRequestDialog dialog;
	private int srcid;

	public NewRequestListener(Controlleur c, int id) {
		this.controlleur = c;
		srcid = id;
		dialog = new NewRequestDialog(this, srcid);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		//System.out.println("   in NewRequestListener.actionPerformed (srcid " + srcid + ")");
		dialog.setVisible(true);
		controlleur.repaint();
	}

	public void createNewRequestRead(long addr, int delay) {
		// TODO: delay not taken into account:
		// this is very hard to do since we don't have any control on the potential
		// future requests sent by the cache L1 (we create proc requests)
		//System.out.println("   in createNewRequestRead(" + addr + ") - (srcid " + srcid + ")");
		controlleur.getTopcell().getProcessor(srcid).addRead(addr);
	}
	
	public void createNewRequestWrite(long addr, long val, int delay) {
		//System.out.println("   in createNewRequestWrite(" + addr + ", " + val + ") - (srcid " + srcid + ")");
		controlleur.getTopcell().getProcessor(srcid).addWrite(addr, val);
	}
	public void createNewRequestSync(int delay) {
		//System.out.println("   in createNewRequestWrite(" + addr + ", " + val + ") - (srcid " + srcid + ")");
		controlleur.getTopcell().getProcessor(srcid).addSync();
	}
	public int getSrcid() {
		return srcid;
	}
	
	public Controlleur getControlleur() {
		return controlleur;
	}
}
