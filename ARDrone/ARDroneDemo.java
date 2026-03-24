package lab.fad;

import javax.swing.JFrame;
import javax.swing.JButton;
import javax.swing.JLabel;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.Toolkit;

import de.yadrone.base.ARDrone;
import de.yadrone.base.IARDrone;

public class ARDroneDemo extends JFrame implements KeyListener {

	private IARDrone drone = null;
	private JButton takeoff_bt, landing_bt, hover_bt, down_bt, up_bt;
	
	public ARDroneDemo() {	
		try {
			drone = new ARDrone();
			drone.start();
		} catch( Exception ex ) {
			// Print MessageBox
			ex.printStackTrace();
			if ( drone != null ) {
				drone.stop();
			}
		}

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setFocusable(true);
		
		setIconImage(Toolkit.getDefaultToolkit().getImage("dte.png"));

		// Añadimos el listener del teclado
		addKeyListener(this);

		// Layout básico
		setLayout(new FlowLayout());
		
		//Añadimos un título a la ventana principal
		setTitle("ARDrone Demo");

		takeoff_bt = new JButton("TakeOFF");		
		takeoff_bt.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				drone.takeOff();
			}
		});
		
		hover_bt = new JButton("Hover");		
		hover_bt.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				drone.hover();
			}
		});

		down_bt = new JButton("Down");
		down_bt.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				drone.down();
			}
		});

		up_bt = new JButton("Up");
		up_bt.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				drone.up();
			}
		});

		landing_bt = new JButton("Landing");
		landing_bt.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				drone.landing();
			}
		});
		
		add (takeoff_bt);
		add (hover_bt);
		add (up_bt);
		add (down_bt);
		add (landing_bt);
	}

	// Interfaz Keylistener	
	public void keyReleased(KeyEvent e)
	{
		//		System.out.println("Key released: " + e.getKeyChar());
		drone.hover();
	}

	public void keyPressed(KeyEvent e)
	{
		//		System.out.println("Key pressed: " + e.getKeyChar()); //  + " (Enter=" + KeyEvent.VK_ENTER + " Space=" + KeyEvent.VK_SPACE + " S=" + KeyEvent.VK_S + " E=" + KeyEvent.VK_E + ")");

		int key = e.getKeyCode();
		int mod = e.getModifiersEx();

		handleCommand(key, mod);
	}

	@Override
	public void keyTyped(KeyEvent e) {}

	protected void handleCommand(int key, int mod)
	{		
		boolean shiftflag = false;
		if ((mod & InputEvent.SHIFT_DOWN_MASK) != 0)
		{
			shiftflag = true;
		}

		switch (key)
		{
			case KeyEvent.VK_ENTER:
				drone.takeOff();
				break;
			case KeyEvent.VK_SPACE:
				drone.landing();
				break;
			case KeyEvent.VK_LEFT:
				if (shiftflag)
				{
					drone.spinLeft();
					shiftflag = false;
				}
				else
					drone.goLeft();
				break;
			case KeyEvent.VK_RIGHT:
				if (shiftflag)
				{
					drone.spinRight();
					shiftflag = false;
				}
				else
					drone.goRight();
				break;
			case KeyEvent.VK_UP:
				if (shiftflag)
				{
					drone.up();
					shiftflag = false;
				}
				else
					drone.forward();
				break;
			case KeyEvent.VK_DOWN:
				if (shiftflag)
				{
					drone.down();
					shiftflag = false;
				}
				else
					drone.backward();
				break;
			case KeyEvent.VK_R:
				drone.spinRight();
				break;
			case KeyEvent.VK_L:
				drone.spinLeft();
				break;
			case KeyEvent.VK_U:
				drone.up();
				break;
			case KeyEvent.VK_D:
				drone.down();
				break;
			case KeyEvent.VK_PLUS:
				drone.setSpeed(drone.getSpeed()+1);
				break;
			case KeyEvent.VK_MINUS:
				drone.setSpeed(drone.getSpeed()-1);
				break;
		}
	}
	
	public static void main(String[] args) {
		ARDroneDemo frame = new ARDroneDemo();
		frame.pack();
		frame.setVisible(true);
	}	
}
