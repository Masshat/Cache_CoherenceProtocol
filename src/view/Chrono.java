package view;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JPanel;

import controller.Controlleur;
import model.Request;
import model.Request.cmd_t;

/**
 * This class, derived from JPanel, constitutes the main chronogram.
 * @author QLM
 * 
 */

public class Chrono extends JPanel {

	private static final long serialVersionUID = 1L;
	
	private static final int pixelsPerCycle = 60;
	private static final int arrowLength = 10;
	
	private static final int startArrowoffset = 0;
	private static final int endArrowoffset = 0;
	
	private static final double cos_alpha = Math.sqrt(3) / 2;
	private static final double sin_alpha = 0.5;

	private Controlleur controlleur;
	
	private ModulePosition modulePositionCM;
	private ModulePosition modulePositionPCM;

	public Chrono(Controlleur c, ModulePosition modulePositionCM, ModulePosition modulePositionPCM) {
		controlleur = c;
		this.modulePositionCM = modulePositionCM;
		this.modulePositionPCM = modulePositionPCM;
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		int nbCycles = controlleur.getTopcell().getNbCycles();
		int nbPixelsY = nbCycles * pixelsPerCycle;
		int nbComponents;
		if (controlleur.getVue().getPanneau().doesDispProcs()) {
			nbComponents = modulePositionPCM.getOrderedModules().size();
		}
		else {
			nbComponents = modulePositionCM.getOrderedModules().size();
		}
		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setPaint(Color.black);
		
		for (int i = 0; i < nbComponents; i++) {
			int x = this.getWidth() / (nbComponents * 2) + i * this.getWidth() / nbComponents;
			g2.drawLine(x, 0, x, nbPixelsY);
			if (nbCycles != 0) {
				g2.drawLine(x, nbPixelsY, x - 4, nbPixelsY - 6);
				g2.drawLine(x, nbPixelsY, x + 4, nbPixelsY - 6);
			}
		}
		
		for (Request req : controlleur.getTopcell().getFinishedCacheRequests()) {
			g2.setPaint(getRequestColor(req.getCmd()));
			int i_start;
			int i_end;
			if (controlleur.getVue().getPanneau().doesDispProcs()) {
				i_start = modulePositionPCM.getModuleIndex(req.getSrcid());
				i_end = modulePositionPCM.getModuleIndex(req.getTgtid());
			}
			else {
				i_start = modulePositionCM.getModuleIndex(req.getSrcid());
				i_end = modulePositionCM.getModuleIndex(req.getTgtid());
			}
			int x_start = this.getWidth() / (nbComponents * 2) + i_start * this.getWidth() / nbComponents;
			int x_end = this.getWidth() / (nbComponents * 2) + i_end * this.getWidth() / nbComponents;
			int y_start = req.getStartCycle() * pixelsPerCycle;
			int y_end = req.getEndCycle() * pixelsPerCycle;
			drawArrow(g2, x_start, y_start, x_end, y_end);
			printRequestInfo(g2, req, (x_start + x_end) / 2, (y_start + y_end) / 2);
		}
		
		// Requests between processors and L1 caches
		if (controlleur.getVue().getPanneau().doesDispProcs()) {
			for (Request req : controlleur.getTopcell().getFinishedProcsRequests()) {
				g2.setPaint(getRequestColor(req.getCmd()));
				int i_start = modulePositionPCM.getModuleIndex(req.getSrcid());
				int i_end = modulePositionPCM.getModuleIndex(req.getTgtid());
				int x_start = this.getWidth() / (nbComponents * 2) + i_start * this.getWidth() / nbComponents;
				int x_end = this.getWidth() / (nbComponents * 2) + i_end * this.getWidth() / nbComponents;
				int y_start = req.getStartCycle() * pixelsPerCycle;
				int y_end = req.getEndCycle() * pixelsPerCycle;
				drawArrow(g2, x_start, y_start + startArrowoffset, x_end, y_end + endArrowoffset);
				printRequestInfo(g2, req, (x_start + x_end) / 2, (y_start + y_end) / 2);
			}
		}

		controlleur.getVue().getPanneau().setScrollSize(nbPixelsY + 5);
	}
	
	
	private void drawArrow(Graphics2D g2, int x_start, int y_start, int x_end, int y_end) {
		double L = Math.abs(x_start - x_end);
		double H = Math.abs(y_start - y_end);
		double D = Math.sqrt(L * L + H * H);
		
		double x_off =     arrowLength * (L / D * cos_alpha - H / D * sin_alpha);
		double b = y_end - arrowLength * (H / D * cos_alpha + L / D * sin_alpha);
		
		double x_off2 =    arrowLength * (L / D * cos_alpha + H / D * sin_alpha);
		double d = y_end - arrowLength * (H / D * cos_alpha - L / D * sin_alpha);
		
		double a, c;
		if (x_start < x_end) {
			a = x_end - x_off;
			c = x_end - x_off2;
		}
		else {
			a = x_end + x_off;
			c = x_end + x_off2;
		}
		g2.drawLine(x_start, y_start, x_end, y_end);
		g2.drawLine(x_end, y_end, (int) a, (int) b);
		g2.drawLine(x_end, y_end, (int) c, (int) d);
	}
	
	private void printRequestInfo(Graphics2D g2, Request req, int x, int y) {
		for (String line : req.toStringBis().split("\n")) {
			int stringLen = (int) g2.getFontMetrics().getStringBounds(line, g2).getWidth();
	        g2.drawString(line, x - stringLen / 2, y += g2.getFontMetrics().getHeight());
		}
	}
	
	private Color getRequestColor(cmd_t cmd) {
		switch (cmd) {
		case READ_WORD:
		case READ_LINE:
		case WRITE_WORD:
		case WRITE_LINE:
		case GETM:
		case GETM_LINE:
			return Color.blue;
		case RSP_READ_WORD:
		case RSP_READ_LINE:
		case RSP_READ_LINE_EX:
		case RSP_WRITE_WORD:
		case RSP_WRITE_LINE:
		case RSP_GETM:
		case RSP_GETM_LINE:
			return Color.magenta;
		case INVAL:
		case INVAL_RO:
			return Color.red;
		case RSP_INVAL_CLEAN:
		case RSP_INVAL_RO_CLEAN:
		case RSP_INVAL_DIRTY:
		case RSP_INVAL_RO_DIRTY:
			return Color.orange;
		case SYNC:
		case RSP_SYNC:
			return Color.GREEN;
		}
		System.out.println("No color for cmd : " + cmd);
		return Color.black;
	}
	
}
