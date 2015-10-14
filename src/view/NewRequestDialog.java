package view;

import java.awt.GridLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import controller.NewRequestListener;

/**
 * This class manages the pop-up window which appears when we click on the "New Request" button
 * An object of this class is a pop-up window
 * @author QLM
 * 
 */

public class NewRequestDialog extends JDialog implements ActionListener, PropertyChangeListener {
	
	private static final long serialVersionUID = 1L;
	
	private int proc_id = 0;
	
	private long address;
	private int value;
	private boolean reqIsRead = true;
	private int delay = -1;
	
	private NewRequestListener listener;
	
	private JPanel mainPanel;
	private JPanel formPanel;
	
	private JComboBox cb;
	private JOptionPane optionPane;
	private JTextField addressTF = new JTextField(10);
	private JTextField valueTF = new JTextField(10);
	// private JTextField delayTF = new JTextField(10);
	private JLabel typeLabel = new JLabel("Request type: ");
	private JLabel addressLabel = new JLabel("Address: ");
	private JLabel valueLabel = new JLabel("Value: ");
	// private JLabel delayLabel = new JLabel("Delay (optionnal): ");
	
	private String [] reqTypes = { "Read", "Write" };
	private String btnString1 = "Enter";
	private String btnString2 = "Cancel";
	
	
	/** Creates the reusable dialog. */
	public NewRequestDialog(NewRequestListener l, int procId) {
		super(l.getControlleur().getVue(), true);
		
		proc_id = procId;
		listener = l;
		
		setTitle("Add a New Request to Processor " + proc_id);
		
		cb = new JComboBox(reqTypes);
		cb.setSelectedIndex(0);
		
		formPanel = new JPanel();
		formPanel.setLayout(new GridLayout(3, 2));
		formPanel.add(typeLabel);
		formPanel.add(cb);
		formPanel.add(addressLabel);
		formPanel.add(addressTF);
		formPanel.add(valueLabel);
		formPanel.add(valueTF);
		// formPanel.add(delayLabel);
		// formPanel.add(delayTF);
		
		mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
		mainPanel.add(formPanel);
		
		// Create an array specifying the number of dialog buttons and their text
		String [] options = { btnString1, btnString2 };
		
		// Create the JOptionPane
		optionPane = new JOptionPane(null, JOptionPane.PLAIN_MESSAGE, JOptionPane.YES_NO_OPTION, null, options, options[0]);
		
		// Make this dialog display it
		mainPanel.add(optionPane);
		setContentPane(mainPanel);
		
		// Handle window closing correctly.
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			
			public void windowClosing(WindowEvent we) {
				/*
				 * Instead of directly closing the window, we're going to change
				 * the JOptionPane's value property.
				 */
				optionPane.setValue(new Integer(JOptionPane.CLOSED_OPTION));
			}
		});
		
		// Ensure the text field always gets the first focus
		addComponentListener(new ComponentAdapter() {
			public void componentShown(ComponentEvent ce) {
				addressTF.requestFocusInWindow();
			}
		});
		
		// Disable value input when starting
		valueTF.setEnabled(false);
		
		// Register an event handler that puts the text into the option pane
		cb.addActionListener(this);
		addressTF.addActionListener(this);
		valueTF.addActionListener(this);
		
		// Register an event handler that reacts to option pane state changes.
		optionPane.addPropertyChangeListener(this);
		
		this.pack();
		this.setVisible(false);
	}
	

	/** This method handles events for the text field. */
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == cb) {
			if (cb.getSelectedItem() == reqTypes[0]) {
				reqIsRead = true;
				valueTF.setEnabled(false);
			}
			else if (cb.getSelectedItem() == reqTypes[1]) {
				reqIsRead = false;
				valueTF.setEnabled(true);
			}
			else {
				assert (false);
			}
		}
		// TODO? traiter les actions des textfields ?
		// activé quand on appuie entrée...
	}
	

	/** This method reacts to state changes in the option pane. */
	public void propertyChange(PropertyChangeEvent e) {
		String prop = e.getPropertyName();
		
		if (!isVisible() || e.getSource() != optionPane || (!JOptionPane.VALUE_PROPERTY.equals(prop) && !JOptionPane.INPUT_VALUE_PROPERTY.equals(prop))) {
			return;
		}
		
		Object val = optionPane.getValue();
		
		if (val == JOptionPane.UNINITIALIZED_VALUE) {
			// This function is called upon reset; ignore it
			return;
		}
		
		// Reset the JOptionPane's value. If we don't do this, then if the user
		// presses the same button next time, no property change event will be
		// fired.
		optionPane.setValue(JOptionPane.UNINITIALIZED_VALUE);
		
		if (btnString1.equals(val)) {
			// The user pressed Enter
			
			// Parsing address
			try {
				address = Long.decode(addressTF.getText());
				if (address < 0) {
					addressTF.selectAll();
					JOptionPane.showMessageDialog(this, "Sorry, \"" + addressTF.getText() + "\" isn't a valid address.\n", null, JOptionPane.ERROR_MESSAGE);
					addressTF.requestFocusInWindow();
					return;
				}
			}
			catch (Exception ex) {
				addressTF.selectAll();
				JOptionPane.showMessageDialog(this, "Sorry, \"" + addressTF.getText() + "\" isn't a valid address.\n", null, JOptionPane.ERROR_MESSAGE);
				addressTF.requestFocusInWindow();
				return;
			}
			
			// Parsing value
			if (!reqIsRead) {
				try {
					value = Integer.parseInt(valueTF.getText());
				}
				catch (Exception ex2) {
					valueTF.selectAll();
					JOptionPane.showMessageDialog(this, "Sorry, \"" + valueTF.getText() + "\" isn't a valid value.\n", null, JOptionPane.ERROR_MESSAGE);
					valueTF.requestFocusInWindow();
					return;
				}
			}

			// clear and hide the dialog
			if (reqIsRead) {
				listener.createNewRequestRead(address, delay);
			}
			else {
				listener.createNewRequestWrite(address, value, delay);
			}
			setVisible(false);
		}
		else {
			// User closed dialog or clicked cancel
			setVisible(false);
		}
	}
	
}
