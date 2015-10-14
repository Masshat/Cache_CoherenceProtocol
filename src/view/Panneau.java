package view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Vector;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import controller.Controlleur;
import controller.NewRequestListener;
import model.L1MesiController;
import model.Module;
import model.Processor;

/**
 * This class is the main panel of the window, and all the inner panels are added to it.
 * @author QLM
 * 
 */
public class Panneau extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final int leftPanelWidth = 160;
	private static final int topRightPanelHeight = 100;

	Dimension dimension;
	Controlleur controlleur;

	private boolean dispProcs = false; // Display processors on the chronogram

	JPanel leftPanel = new JPanel();
	JPanel rightPanel = new JPanel();
	JPanel topRightPanelCM = new JPanel(); // topRightPanel with caches and memories
	JPanel topRightPanelPCM = new JPanel(); // topRightPanel with processors, caches and memories
	JPanel bottomRightPanel = new JPanel();
	Chrono chrono;
	JButton nextCycle = new JButton("Next Cycle");
	JButton showProcs = new JButton("Show Procs");
	JScrollPane scrollPane = new JScrollPane();

	Vector<JButton> newRequest = new Vector<JButton>();

	ModulePosition modulePositionCM;
	ModulePosition modulePositionPMC;

	public Panneau(Dimension dim) {
		dimension = dim;
		this.setSize(dim);
		this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));

		leftPanel.add(nextCycle);
		nextCycle.setAlignmentX(JPanel.CENTER_ALIGNMENT);

		leftPanel.add(showProcs);
		showProcs.setAlignmentX(JPanel.CENTER_ALIGNMENT);

		leftPanel.setPreferredSize(new Dimension(leftPanelWidth, dim.height));

		topRightPanelCM.setPreferredSize(new Dimension(dim.width
				- leftPanelWidth, topRightPanelHeight));
		topRightPanelPCM.setPreferredSize(new Dimension(dim.width
				- leftPanelWidth, topRightPanelHeight));

		bottomRightPanel.setLayout(new BorderLayout());
		bottomRightPanel.setPreferredSize(new Dimension(dim.width, dim.height
				- topRightPanelHeight));

		scrollPane.getVerticalScrollBar().setUnitIncrement(20);

		bottomRightPanel.add(scrollPane, BorderLayout.CENTER);

		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
		if (dispProcs) {
			rightPanel.add(topRightPanelPCM);
		}
		else {
			rightPanel.add(topRightPanelCM);
		}
		rightPanel.add(bottomRightPanel);

		this.add(leftPanel);
		this.add(rightPanel);
	}

	@Override
	public Dimension getPreferredSize() {
		return dimension;
	}

	public void setControlleur(Controlleur c) {
		controlleur = c;

		modulePositionCM = controlleur.buildModulePositionCM();
		modulePositionPMC = controlleur.buildModulePositionPCM();
		controlleur.buildNewRequestListeners();
		
		nextCycle.addActionListener(c.getNextCycleListener());
		showProcs.addActionListener(c.getShowProcsListener());
		topRightPanelCM.setLayout(new GridLayout(2, modulePositionCM.getOrderedModules().size()));
		topRightPanelPCM.setLayout(new GridLayout(2, modulePositionPMC.getOrderedModules().size()));

		chrono = new Chrono(c, modulePositionCM, modulePositionPMC);
		chrono.setBackground(Color.white);
		chrono.setPreferredSize(new Dimension(dimension.width - leftPanelWidth
				- 10, dimension.height - topRightPanelHeight));

		scrollPane.getViewport().add(chrono);
		scrollPane.getViewport().revalidate();

		// variable used to make the correspondence between the "New Request" buttons between the two possible top panels  
		Vector<NewRequestListener> reqListeners = new Vector<NewRequestListener>();
		
		// Building top panel with processors
		for (Module m : modulePositionPMC.getOrderedModules()) {
			JLabel label = new JLabel(m.getName());
			JPanel upPanel = new JPanel();
			upPanel.setLayout(new BoxLayout(upPanel, BoxLayout.Y_AXIS));
			upPanel.add(label);
			label.setAlignmentX(JPanel.CENTER_ALIGNMENT);
			label.setAlignmentY(JPanel.CENTER_ALIGNMENT);

			topRightPanelPCM.add(upPanel);
		}
		
		for (Module m : modulePositionPMC.getOrderedModules()) {
			JPanel downPanel = new JPanel();
			downPanel.setLayout(new BoxLayout(downPanel, BoxLayout.Y_AXIS));
			if (m instanceof Processor) {
				JButton button = new JButton("Add Request");
				
				NewRequestListener newReqListener = controlleur.getNewRequestListener(m.getSrcid());
				reqListeners.add(newReqListener);
				button.addActionListener(newReqListener);
				
				downPanel.add(button);
				button.setAlignmentX(JPanel.CENTER_ALIGNMENT);
				button.setAlignmentY(JPanel.CENTER_ALIGNMENT);
			}
			else {
				JLabel empty = new JLabel(" ");
				downPanel.add(empty);
				empty.setAlignmentX(JPanel.CENTER_ALIGNMENT);
				empty.setAlignmentY(JPanel.CENTER_ALIGNMENT);
			}
			topRightPanelPCM.add(downPanel);
		}
		
		
		// Building top panel without processors
		for (Module m : modulePositionCM.getOrderedModules()) {
			assert (!(m instanceof Processor));
			JLabel label = new JLabel(m.getName());
			JPanel upPanel = new JPanel();
			upPanel.setLayout(new BoxLayout(upPanel, BoxLayout.Y_AXIS));
			upPanel.add(label);
			label.setAlignmentX(JPanel.CENTER_ALIGNMENT);
			label.setAlignmentY(JPanel.CENTER_ALIGNMENT);
			
			topRightPanelCM.add(upPanel);
		}
		
		int listenerIndex = 0;
		for (Module m : modulePositionCM.getOrderedModules()) {
			JPanel downPanel = new JPanel();
			downPanel.setLayout(new BoxLayout(downPanel, BoxLayout.Y_AXIS));
			if (m instanceof L1MesiController) {
				JButton button = new JButton("Add Request");
				button.addActionListener(reqListeners.get(listenerIndex));
				downPanel.add(button);
				button.setAlignmentX(JPanel.CENTER_ALIGNMENT);
				button.setAlignmentY(JPanel.CENTER_ALIGNMENT);
				listenerIndex++;
			}
			else {
				JLabel empty = new JLabel(" ");
				downPanel.add(empty);
				empty.setAlignmentX(JPanel.CENTER_ALIGNMENT);
				empty.setAlignmentY(JPanel.CENTER_ALIGNMENT);
			}
			topRightPanelCM.add(downPanel);
		}
		
	}

	public void setScrollSize(int size) {
		chrono.setPreferredSize(new Dimension(dimension.width - leftPanelWidth - 10, size));
		scrollPane.getViewport().revalidate();
	}

	public void setScrollBarMax() {
		scrollPane.getVerticalScrollBar().setValue(
				scrollPane.getVerticalScrollBar().getMaximum());
	}

	public void switchShowProcs() {
		if (dispProcs) {
			dispProcs = false;
			showProcs.setText("Show Procs");
			rightPanel.remove(topRightPanelPCM);
			rightPanel.add(topRightPanelCM, 0);
		}
		else {
			dispProcs = true;
			showProcs.setText("Hide Procs");
			rightPanel.remove(topRightPanelCM);
			rightPanel.add(topRightPanelPCM, 0);
		}
	}

	public boolean doesDispProcs() {
		return dispProcs;
	}
}
