package com.bobrust.gui;

import java.awt.*;
import java.awt.Dialog.ModalityType;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import com.bobrust.generator.BorstGenerator.BorstData;
import com.bobrust.lang.RustTranslator;
import com.bobrust.generator.BorstSettings;
import com.bobrust.logging.LogUtils;

/**
 * Overlay window that will cover the entire screen
 * 
 * @author HardCoded
 */
@SuppressWarnings("serial")
public class BobRustOverlay extends JPanel {
	private static final int RECTANGLE_SELECTION_SIZE = 10;
	private static final int BORDER_SIZE = 3;
	private static final Dimension DEFAULT_DIALOG_SIZE = new Dimension(148, 448);
	
	public final JDialog dialog;
	private final BobRustEditor gui;
	private OverlayType action = OverlayType.NONE;
	private ResizeOption resizeOption = ResizeOption.NONE;
	
	private final Rectangle drawRegion = new Rectangle(0, 0, 0, 0);
	private final Rectangle imageRegion = new Rectangle(0, 0, 0, 0);
	
	// Used when drawing the selection box.
	private final Point colorRegion = new Point(0, 0);
	private final Point dragStart = new Point(0, 0);
	private final Point dragEnd = new Point(0, 0);
	
	private final BobRustMonitorPicker monitorPicker;
	private final BobRustSettings settingsGui;
	
	private final JButton btnSelectMonitor;
	private final JToggleButton btnSelectCanvasRegion;
	private final JToggleButton btnSelectImageRegion;
	private final JButton btnSelectColorRegion;
	private final JButton btnOpenImage;
	private final JButton btnStartGenerate;
	private final JButton btnStopGenerate;
	
	private BufferedImage image;
	private BufferedImage modelImage;
	private JButton btnOptions;
	private JButton btnMaximize;
	private JPanel actionPanel;
	
	private boolean isFullscreen;
	private java.util.List<JLabel> labels = new ArrayList<>();
	private final JLabel generationLabel;
	private final JLabel generationInfo;
	private final JPanel topBarPanel;
	private final JButton btnPauseGenerate;
	
	protected BobRustOverlay(BobRustEditor gui) {
		this.gui = gui;
		
		dialog = new JDialog(null, "BobRust", ModalityType.APPLICATION_MODAL);
		dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
		dialog.setSize(DEFAULT_DIALOG_SIZE);
		dialog.setResizable(false);
		dialog.setLocationRelativeTo(null);
		dialog.setFocusable(false);
		dialog.setFocusableWindowState(true);
		dialog.setContentPane(this);
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
		
		settingsGui = new BobRustSettings(gui, dialog);
		monitorPicker = new BobRustMonitorPicker(dialog);
		
		MouseAdapter mouseAdapter = new MouseAdapter() {
			private Point originPoint = new Point(0, 0);
			private Rectangle original = new Rectangle();
			private Rectangle rectangle = new Rectangle();
			private boolean isToolbarArea;
			
			@Override
			public void mousePressed(MouseEvent e) {
				isToolbarArea = e.getX() < 137 - RECTANGLE_SELECTION_SIZE;
				if(isToolbarArea) {
					// If the point is in the action panel we should not compute anything
					return;
				}
				
				originPoint = new Point(e.getPoint());
				if(originPoint.x < 137) {
					originPoint.x = 137;
				}
				if(action == OverlayType.SELECT_CANVAS_REGION) rectangle = drawRegion;
				if(action == OverlayType.SELECT_IMAGE_REGION) rectangle = imageRegion;
				
				switch(action) {
					case SELECT_COLOR_REGION -> {
						colorRegion.setLocation(e.getPoint());
						endSelectRegion();
					}
					case SELECT_CANVAS_REGION, SELECT_IMAGE_REGION -> {
						// Keep a copy of the original.
						original.setBounds(rectangle);
						
						Rectangle larger = new Rectangle(rectangle);
						larger.setBounds(
							rectangle.x - RECTANGLE_SELECTION_SIZE,
							rectangle.y - RECTANGLE_SELECTION_SIZE,
							rectangle.x + rectangle.width + RECTANGLE_SELECTION_SIZE * 2,
							rectangle.y + rectangle.height + RECTANGLE_SELECTION_SIZE * 2
						);
						
						Point mouse = e.getPoint();
						
						if(!larger.contains(mouse)) {
							resizeOption = ResizeOption.ALL;
						} else {
							boolean top = Math.abs(rectangle.y - mouse.y) < RECTANGLE_SELECTION_SIZE;
							boolean right = Math.abs(rectangle.x + rectangle.width - mouse.x) < RECTANGLE_SELECTION_SIZE;
							boolean bottom  = Math.abs(rectangle.y + rectangle.height - mouse.y) < RECTANGLE_SELECTION_SIZE;
							boolean left = Math.abs(rectangle.x - mouse.x) < RECTANGLE_SELECTION_SIZE;
							
							if(top) {
								resizeOption = right ? ResizeOption.TOP_RIGHT:(left ? ResizeOption.TOP_LEFT:ResizeOption.TOP);
							} else if(bottom) {
								resizeOption = right ? ResizeOption.BOTTOM_RIGHT:(left ? ResizeOption.BOTTOM_LEFT:ResizeOption.BOTTOM);
							} else {
								resizeOption = right ? ResizeOption.RIGHT:(left ? ResizeOption.LEFT:ResizeOption.ALL);
							}
						}
						
						modifyRectangle(e.getPoint());
					}
					default -> {
						
					}
				}
			}
			
			public void mouseMoved(MouseEvent e) {
				if(isToolbarArea) {
					return;
				}
				
				if(action == OverlayType.SELECT_CANVAS_REGION) rectangle = drawRegion;
				if(action == OverlayType.SELECT_IMAGE_REGION) rectangle = imageRegion;
				
				switch(action) {
					case SELECT_COLOR_REGION -> {
						Point point = e.getPoint();
						if(point.x < 135) {
							point.x = 135;
						}
						colorRegion.setLocation(point);
						repaint();
					}
					case SELECT_CANVAS_REGION, SELECT_IMAGE_REGION -> {
						// Keep a copy of the original.
						original.setBounds(rectangle);
						
						Point mouse = e.getPoint();
						
						Rectangle larger = new Rectangle(rectangle);
						larger.setBounds(
							rectangle.x - RECTANGLE_SELECTION_SIZE,
							rectangle.y - RECTANGLE_SELECTION_SIZE,
							rectangle.width + RECTANGLE_SELECTION_SIZE * 2,
							rectangle.height + RECTANGLE_SELECTION_SIZE * 2
						);
						
						if(!larger.contains(mouse)) {
							resizeOption = ResizeOption.ALL;
						} else {
							boolean top = Math.abs(rectangle.y - mouse.y) < RECTANGLE_SELECTION_SIZE;
							boolean right = Math.abs(rectangle.x + rectangle.width - mouse.x) < RECTANGLE_SELECTION_SIZE;
							boolean bottom  = Math.abs(rectangle.y + rectangle.height - mouse.y) < RECTANGLE_SELECTION_SIZE;
							boolean left = Math.abs(rectangle.x - mouse.x) < RECTANGLE_SELECTION_SIZE;
							
							if(top) {
								resizeOption = right ? ResizeOption.TOP_RIGHT:(left ? ResizeOption.TOP_LEFT:ResizeOption.TOP);
							} else if(bottom) {
								resizeOption = right ? ResizeOption.BOTTOM_RIGHT:(left ? ResizeOption.BOTTOM_LEFT:ResizeOption.BOTTOM);
							} else {
								resizeOption = right ? ResizeOption.RIGHT:(left ? ResizeOption.LEFT:ResizeOption.ALL);
							}
						}
						
						repaint();
					}
					default -> {}
				}
			}
			
			private void modifyRectangle(Point point) {
				if(point.x < 137) {
					// Do not allow intersection with the toolbar area.
					point.x = 137;
				}
				
				Point topLeft = new Point(original.x, original.y);
				Point bottomRight = new Point(original.x + original.width, original.y + original.height);
				
				switch(resizeOption) {
					case ALL -> {
						topLeft = originPoint;
						bottomRight = point;
					}
					default -> {
						if(resizeOption.top) topLeft.y = point.y;
						if(resizeOption.right) bottomRight.x = point.x;
						if(resizeOption.bottom) bottomRight.y = point.y;
						if(resizeOption.left) topLeft.x = point.x;
					}
				}
				
				dragStart.x = topLeft.x;
				dragStart.y = topLeft.y;
				dragEnd.x = bottomRight.x;
				dragEnd.y = bottomRight.y;
				
				int x = Math.min(topLeft.x, bottomRight.x);
				int y = Math.min(topLeft.y, bottomRight.y);
				int width = Math.abs(topLeft.x - bottomRight.x);
				int height = Math.abs(topLeft.y - bottomRight.y);
				rectangle.setBounds(x, y, width, height);
			}
			
			@Override
			public void mouseDragged(MouseEvent e) {
				if(isToolbarArea) {
					return;
				}
				
				switch(action) {
					case SELECT_CANVAS_REGION, SELECT_COLOR_REGION, SELECT_IMAGE_REGION -> {
						modifyRectangle(e.getPoint());
						repaint();
					}
					default -> {}
				}
			}
			
			@Override
			public void mouseReleased(MouseEvent e) {
				if(isToolbarArea) {
					isToolbarArea = false;
					return;
				}
				
				switch(action) {
					case SELECT_CANVAS_REGION, SELECT_COLOR_REGION, SELECT_IMAGE_REGION -> {
						modifyRectangle(e.getPoint());
						
						dragStart.x = rectangle.x;
						dragStart.y = rectangle.y;
						dragEnd.x = rectangle.x + rectangle.width;
						dragEnd.y = rectangle.y + rectangle.height;
						resizeOption = ResizeOption.NONE;
						
						repaint();
					}
					default -> {}
				}
			}
		};
		
		dialog.addMouseListener(mouseAdapter);
		dialog.addMouseMotionListener(mouseAdapter);
		
		this.setOpaque(false);
		this.setBackground(new Color(0, true));
		this.setLayout(null);

		actionPanel = new JPanel();
		actionPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		actionPanel.setLayout(new BoxLayout(actionPanel, BoxLayout.Y_AXIS));
		actionPanel.setBackground(Color.WHITE);
		actionPanel.setBounds(0, 0, 132, 500);
		add(actionPanel);
		
		topBarPanel = new JPanel();
		topBarPanel.setBounds(150, 5, 10, 10);
		topBarPanel.setBackground(new Color(0x7f000000, true));
		topBarPanel.setLayout(null);
		add(topBarPanel);
		
		generationLabel = new JLabel("No active generation");
		generationLabel.setHorizontalAlignment(SwingConstants.CENTER);
		generationLabel.setHorizontalTextPosition(SwingConstants.CENTER);
		generationLabel.setForeground(Color.DARK_GRAY);
		generationLabel.setFont(generationLabel.getFont().deriveFont(18.0f));
		generationLabel.setBounds(0, 0, 380, 25);
		generationLabel.setBackground(Color.blue);
		topBarPanel.add(generationLabel);
		
		generationInfo = new JLabel("");
		generationInfo.setHorizontalAlignment(SwingConstants.CENTER);
		generationInfo.setHorizontalTextPosition(SwingConstants.CENTER);
		generationInfo.setForeground(Color.WHITE);
		generationInfo.setFont(generationInfo.getFont().deriveFont(Font.BOLD, 16.0f));
		generationInfo.setBounds(0, 20, 440, 20);
		generationInfo.setBackground(Color.red);
		topBarPanel.add(generationInfo);
		
		Dimension buttonSize = new Dimension(120, 24);
		
		JLabel lblOptions = new JLabel("Options");
		lblOptions.setForeground(Color.BLACK);
		actionPanel.add(lblOptions);
		labels.add(lblOptions);
		
		btnSelectMonitor = new JButton("Select Monitor");
		btnSelectMonitor.setMaximumSize(buttonSize);
		btnSelectMonitor.setFocusable(false);
		btnSelectMonitor.setOpaque(false);
		btnSelectMonitor.addActionListener((event) -> {
			GraphicsConfiguration gc = monitorPicker.openDialog();
			if(isFullscreen) {
				dialog.setBounds(gc.getBounds());
			}
			Rectangle bounds = gc.getBounds();
			String idString = gc.getDevice().getIDstring();
			LogUtils.info("Selected Monitor: { id: '%s', x: %d, y: %d, width: %d, height: %d }", idString, bounds.x, bounds.y, bounds.width, bounds.height);
		});
		
		btnMaximize = new JButton("Make Fullscreen");
		btnMaximize.setOpaque(false);
		btnMaximize.setMaximumSize(new Dimension(120, 24));
		btnMaximize.setFocusable(false);
		btnMaximize.addActionListener(this::changeFullscreen);
		actionPanel.add(btnMaximize);
		actionPanel.add(btnSelectMonitor);
		
		btnOpenImage = new JButton("Load Image");
		btnOpenImage.setMaximumSize(buttonSize);
		btnOpenImage.setFocusable(false);
		btnOpenImage.setOpaque(false);
		btnOpenImage.addActionListener((event) -> {
			File file = gui.openImageFileChooser(dialog);
			
			if(file != null) {
				try {
					BufferedImage selectedImage = ImageIO.read(file);
					LogUtils.info("Loaded image '%s'", file);
					gui.borstSettings.ImagePath = file.getAbsolutePath();
					image = selectedImage;
					updateButtons();
				} catch(IOException e) {
					LogUtils.error("Failed to read image '%s'", file);
				} catch(Throwable t) {
					t.printStackTrace();
				}
				
				repaint();
			}
		});
		actionPanel.add(btnOpenImage);
		
		btnOptions = new JButton("Options");
		btnOptions.setOpaque(false);
		btnOptions.setMaximumSize(buttonSize);
		btnOptions.setFocusable(false);
		btnOptions.addActionListener((event) -> {
			settingsGui.openDialog(btnOptions.getLocationOnScreen());
			updateButtons();
		});
		actionPanel.add(btnOptions);

		JLabel lblRegions = new JLabel("Regions");
		lblRegions.setBorder(new EmptyBorder(10, 0, 0, 0));
		lblRegions.setForeground(Color.BLACK);
		actionPanel.add(lblRegions);
		labels.add(lblRegions);
		
		btnSelectCanvasRegion = new JToggleButton("Canvas Region");
		btnSelectCanvasRegion.setMaximumSize(buttonSize);
		btnSelectCanvasRegion.setFocusable(false);
		btnSelectCanvasRegion.setOpaque(false);
		btnSelectCanvasRegion.setEnabled(false);
		btnSelectCanvasRegion.addActionListener((event) -> {
			boolean isSelected = btnSelectCanvasRegion.isSelected();
			
			if(isSelected) {
				startSelectRegion(drawRegion, OverlayType.SELECT_CANVAS_REGION);
			} else {
				endSelectRegion();
			}
		});
		actionPanel.add(btnSelectCanvasRegion);
		
		btnSelectImageRegion = new JToggleButton("Image Region");
		btnSelectImageRegion.setMaximumSize(buttonSize);
		btnSelectImageRegion.setOpaque(false);
		btnSelectImageRegion.setFocusable(false);
		btnSelectImageRegion.setEnabled(false);
		btnSelectImageRegion.addActionListener((event) -> {
			boolean isSelected = btnSelectImageRegion.isSelected();
			
			if(isSelected) {
				startSelectRegion(imageRegion, OverlayType.SELECT_IMAGE_REGION);
			} else {
				endSelectRegion();
			}
		});
		actionPanel.add(btnSelectImageRegion);
		
		btnSelectColorRegion = new JButton("Color Region");
		btnSelectColorRegion.setOpaque(false);
		btnSelectColorRegion.setEnabled(false);
		btnSelectColorRegion.setFocusable(false);
		btnSelectColorRegion.setMaximumSize(buttonSize);
		btnSelectColorRegion.addActionListener((event) -> {
			startSelectRegion(null, OverlayType.SELECT_COLOR_REGION);
		});
		actionPanel.add(btnSelectColorRegion);
		
		JLabel lblActions = new JLabel("Preview Actions");
		lblActions.setForeground(Color.BLACK);
		lblActions.setBorder(new EmptyBorder(10, 0, 0, 0));
		actionPanel.add(lblActions);
		labels.add(lblActions);
		
		btnStartGenerate = new JButton("Start Generate");
		btnStopGenerate = new JButton("Stop Generate");
		btnStartGenerate.setMaximumSize(buttonSize);
		btnStartGenerate.setEnabled(false);
		btnStartGenerate.setFocusable(false);
		btnStartGenerate.setOpaque(false);
		btnStartGenerate.addActionListener((event) -> {
			if(!gui.borstGenerator.isRunning()) {
				
				Rectangle rect = drawRegion.createIntersection(imageRegion).getBounds();
				
				if(!rect.isEmpty()) {
					Sign signType = gui.getSettingsSign();
					Color bgColor = gui.getSettingsBackgroundCalculated();
					
					BufferedImage clip = new BufferedImage(drawRegion.width, drawRegion.height, BufferedImage.TYPE_INT_ARGB);
					Graphics2D g = clip.createGraphics();
					g.drawImage(image, imageRegion.x - drawRegion.x, imageRegion.y - drawRegion.y, imageRegion.width, imageRegion.height, null);
					g.dispose();
					
					BufferedImage scaled = new BufferedImage(signType.width, signType.height, BufferedImage.TYPE_INT_ARGB);
					g = scaled.createGraphics();
					g.setColor(bgColor);
					g.fillRect(0, 0, scaled.getWidth(), scaled.getHeight());
					g.drawImage(clip, 0, 0, scaled.getWidth(), scaled.getHeight(), null);
					g.dispose();
					
					BorstSettings settings = gui.borstSettings;
					settings.Background = bgColor.getRGB();
					settings.Width = scaled.getWidth();
					settings.Height = scaled.getHeight();
					settings.DirectImage = scaled;
					updateEditor();
					if(gui.borstGenerator.start()) {
						action = OverlayType.GENERATE_IMAGE;
						updateButtons();
					}
				}
				
				repaint();
			}
		});
		actionPanel.add(btnStartGenerate);
		
		btnStopGenerate.setOpaque(false);
		btnStopGenerate.setMaximumSize(new Dimension(120, 24));
		btnStopGenerate.setFocusable(false);
		btnStopGenerate.setEnabled(false);
		btnStopGenerate.addActionListener((event) -> {
			if(gui.borstGenerator.isRunning()) {
				try {
					gui.borstSettings.DirectImage = null;
					gui.borstGenerator.stop();
					modelImage = null;
				} catch(InterruptedException e) {
					e.printStackTrace();
				}
				
				action = OverlayType.NONE;
				updateButtons();
				updateEditor();
				repaint();
			}
		});
		actionPanel.add(btnStopGenerate);
		
		btnPauseGenerate = new JButton("Pause Generate");
		btnPauseGenerate.setOpaque(false);
		btnPauseGenerate.setMaximumSize(buttonSize);
		btnPauseGenerate.setFocusable(false);
		btnPauseGenerate.setEnabled(false);
		btnPauseGenerate.addActionListener((event) -> {
			if(gui.borstGenerator.isPaused()) {
				btnPauseGenerate.setText("Pause Generate");
				gui.borstGenerator.resume();
			} else {
				btnPauseGenerate.setText("Resume Generate");
				gui.borstGenerator.pause();
			}
		});
		actionPanel.add(btnPauseGenerate);
		
		JButton btnClose = new JButton("Close");
		btnClose.setOpaque(false);
		btnClose.setMaximumSize(buttonSize);
		btnClose.setFocusable(false);
		btnClose.addActionListener((event) -> {
			int dialogResult = JOptionPane.showConfirmDialog(dialog, "Do you want to close the application?", "Warning", JOptionPane.YES_NO_OPTION);
			if(dialogResult == JOptionPane.YES_OPTION){
				System.exit(0);
			}
		});
		actionPanel.add(btnClose);
		
		Dimension textDimension = new Dimension(120, 24);
		
		JPanel panel_1 = new JPanel();
		panel_1.setOpaque(false);
		panel_1.setBorder(new EmptyBorder(1, 1, 1, 1));
		panel_1.setAlignmentX(Component.LEFT_ALIGNMENT);
		actionPanel.add(panel_1);
		panel_1.setMaximumSize(textDimension);
		panel_1.setMinimumSize(textDimension);
		panel_1.setLayout(new BoxLayout(panel_1, BoxLayout.X_AXIS));
		
		JLabel lblHelp = new JLabel("Help");
		lblHelp.setForeground(Color.BLACK);
		lblHelp.setBorder(new EmptyBorder(10, 0, 0, 0));
		panel_1.add(lblHelp);
		labels.add(lblHelp);
		
		JButton btnGithubIssue = new JButton("Report Issue");
		btnGithubIssue.setOpaque(false);
		btnGithubIssue.setMaximumSize(new Dimension(120, 24));
		btnGithubIssue.setFocusable(false);
		btnGithubIssue.addActionListener((event) -> {
			gui.openIssueUrl();
		});
		actionPanel.add(btnGithubIssue);
		
		JButton btnAbout = new JButton("About");
		btnAbout.setOpaque(false);
		btnAbout.setMaximumSize(new Dimension(120, 24));
		btnAbout.setFocusable(false);
		btnAbout.addActionListener((event) -> {
			String message = """
							 Created by HardCoded & Sekwah41
							 """;
			
			JOptionPane.showMessageDialog(dialog, message, "About me", JOptionPane.INFORMATION_MESSAGE);
		});
		actionPanel.add(btnAbout);
		
		updateEditor();
	}
	
	public void updateEditor() {
		setBorder(isFullscreen ? new LineBorder(gui.getBorderColor(), BORDER_SIZE):null);
		actionPanel.setBackground(gui.getToolbarColor());
		
		for(JLabel label : labels) {
			label.setForeground(gui.getLabelColor());
		}
		
		generationLabel.setLocation((topBarPanel.getWidth() - generationLabel.getWidth()) / 2, generationLabel.getY());
		generationLabel.setText("0/%d shapes generated".formatted(gui.getSettingsMaxShapes()));
		
		generationInfo.setLocation((topBarPanel.getWidth() - generationInfo.getWidth()) / 2, generationInfo.getY());
		generationInfo.setText("Estimated 0 seconds");
		
		btnPauseGenerate.setText(gui.borstGenerator.isPaused() ? "Resume Generate":"Pause Generate");
	}
	
	private void updateButtons() {
		boolean defaultAction = action == OverlayType.NONE;
		
		// You should only be able to pick monitor the screen is enabled
		btnSelectMonitor.setEnabled(defaultAction);
		
		btnSelectCanvasRegion.setEnabled(isFullscreen && defaultAction || action == OverlayType.SELECT_CANVAS_REGION);
		btnSelectImageRegion.setEnabled(isFullscreen && defaultAction && image != null || action == OverlayType.SELECT_IMAGE_REGION);
		btnSelectColorRegion.setEnabled(isFullscreen && defaultAction || action == OverlayType.SELECT_COLOR_REGION);
		
		btnMaximize.setEnabled(true);
		btnOpenImage.setEnabled(defaultAction);
		btnStartGenerate.setEnabled(isFullscreen && defaultAction && image != null && !gui.borstGenerator.isRunning());
		btnPauseGenerate.setEnabled(isFullscreen && gui.borstGenerator.isRunning());
		btnStopGenerate.setEnabled(gui.borstGenerator.isRunning());
	}

	private void changeFullscreen(ActionEvent event) {
		isFullscreen = !isFullscreen;
		btnMaximize.setText(isFullscreen ? "Minimize":"Make Fullscreen");
		actionPanel.setLocation(isFullscreen ? BORDER_SIZE:0, isFullscreen ? BORDER_SIZE:0);
		
		dialog.setVisible(false);
		dialog.dispose();
		
		updateButtons();
		
		dialog.setAlwaysOnTop(isFullscreen);
		if(!isFullscreen) {
			dialog.setBackground(Color.lightGray);
			dialog.setUndecorated(false);
			dialog.setSize(DEFAULT_DIALOG_SIZE);
		} else {
			dialog.setUndecorated(true);
			dialog.setBackground(new Color(0, true));
			GraphicsConfiguration gc = monitorPicker.getMonitor();
			dialog.setBounds(gc.getBounds());
		}
		actionPanel.setSize(actionPanel.getWidth(), dialog.getHeight() - BORDER_SIZE * 2);
		topBarPanel.setBounds(BORDER_SIZE + (dialog.getWidth() - 440) / 2, isFullscreen ? BORDER_SIZE:-50, 440, 40);
		updateEditor();
		dialog.setVisible(true);
	}
	
	public void onBorstCallback(BorstData data) {
		this.modelImage = data.getModel().current.image;
		this.repaint();
		LogUtils.info("Processing %d/%d", data.getIndex(), gui.borstSettings.MaxShapes);
		
		generationLabel.setText("%d/%d shapes generated".formatted(data.getIndex(), gui.borstSettings.MaxShapes));
		generationInfo.setText("Estimated %s".formatted(RustTranslator.getTimeMinutesMessage((long)(data.getIndex() * 1.5 * (1000 / 30L)))));
	}
	
	@Override
	protected void paintComponent(Graphics gr) {
		super.paintComponent(gr);
		if(!isFullscreen) return;
		
		Graphics2D g = (Graphics2D)gr;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0, true));
		g.fillRect(0, 0, getWidth(), getHeight());
		
		// Draw monitor border
		{
			g.setColor(new Color(0.3f, 0.3f, 0.3f));
			g.fillRect(0, 0, 135, getHeight());
			
			if(action == OverlayType.SELECT_CANVAS_REGION
			|| action == OverlayType.SELECT_IMAGE_REGION
			|| action == OverlayType.SELECT_COLOR_REGION) {
				g.setColor(new Color(0x30000000, true));
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		}
		
		// Draw region outline
		{
			Stroke old_stroke = g.getStroke();
			g.setStroke(new BasicStroke(5.0f));
			
			BufferedImage img = image;
			if(img != null) {
				if(!gui.borstGenerator.isRunning()) {
					g.drawImage(img, imageRegion.x, imageRegion.y, imageRegion.width, imageRegion.height, null);
				}
				drawRectangle(g, imageRegion, Color.cyan, action == OverlayType.SELECT_IMAGE_REGION);
			}
			
			if(drawRegion.x != 0 && drawRegion.y != 0) {
				drawRectangle(g, drawRegion, Color.yellow, action == OverlayType.SELECT_CANVAS_REGION);
			}
			
			g.setStroke(old_stroke);
		}
		
		if(action == OverlayType.DRAW_IMAGE) {
			Composite old_composite = g.getComposite();
			g.setComposite(AlphaComposite.Clear);
			g.setColor(new Color(0.5f, 0.7f, 0.2f, 0.5f));
			g.fillRect(drawRegion.x, drawRegion.y, drawRegion.width, drawRegion.height);
			g.setComposite(old_composite);
		}
		
		// Draw model image
		{
			BufferedImage image = modelImage;
			if(image != null) {
				g.setColor(new Color(gui.borstSettings.Background));
				g.fillRect(drawRegion.x, drawRegion.y, drawRegion.width, drawRegion.height);
				g.drawImage(image, drawRegion.x, drawRegion.y, drawRegion.width, drawRegion.height, null);
			}
		}
		
		// Draw Color Palette
		{
			BufferedImage bi = BobRustConstants.COLOR_PALETTE;
			Point region = colorRegion;
			if(action == OverlayType.SELECT_COLOR_REGION) {
				g.drawImage(bi, region.x - bi.getWidth() / 2, region.y - bi.getHeight() / 2, null);
			}
			
			if(region.x != 0 && region.y != 0) {
				g.setColor(Color.red);
				g.drawRect(region.x - bi.getWidth() / 2, region.y - bi.getHeight() / 2, bi.getWidth(), bi.getHeight());
			}
		}
	}
	
	private void drawRectangle(Graphics2D g, Rectangle rect, Color light, boolean selected) {
		Stroke old_stroke = g.getStroke();
		
		float weight = selected ? 5:1;
		BasicStroke def_stroke = new BasicStroke(weight, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		BasicStroke sel_stroke = new BasicStroke(weight, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		Color dark = light.darker();
		
		if(!selected) {
			g.setStroke(def_stroke);
			
			g.setColor(dark);
			g.drawRect(rect.x + 1, rect.y + 1, rect.width - 2, rect.height - 2);
			g.drawRect(rect.x - 1, rect.y - 1, rect.width + 2, rect.height + 2);
			
			g.setColor(light);
			g.drawRect(rect.x, rect.y, rect.width, rect.height);
		} else {
			ResizeOption option = resizeOption;
			
			if(option == ResizeOption.ALL) {
				option = ResizeOption.NONE;
			}
			
			Point start = dragStart;
			Point end = dragEnd;

			// Draw unselected lines
			g.setStroke(def_stroke);
			g.setColor(light);
			if(!option.top) g.drawLine(start.x, start.y, end.x, start.y);
			if(!option.right) g.drawLine(end.x, start.y, end.x, end.y);
			if(!option.bottom) g.drawLine(start.x, end.y, end.x, end.y);
			if(!option.left) g.drawLine(start.x, start.y, start.x, end.y);
			
			// Draw selected lines
			g.setStroke(sel_stroke);
			g.setColor(dark);
			if(option.top) g.drawLine(start.x, start.y, end.x, start.y);
			if(option.right) g.drawLine(end.x, start.y, end.x, end.y);
			if(option.bottom) g.drawLine(start.x, end.y, end.x, end.y);
			if(option.left) g.drawLine(start.x, start.y, start.x, end.y);
		}
		
		g.setStroke(old_stroke);
	}
	
	protected synchronized void startSelectRegion(Rectangle region, OverlayType type) {
		if(action != OverlayType.NONE) {
			return;
		}
		action = type;
		
		dialog.setBounds(monitorPicker.getMonitor().getBounds());
		if(region != null) {
			dragStart.setLocation(region.x, region.y);
			dragEnd.setLocation(region.x + region.width, region.y + region.height);
		}
		updateButtons();
		repaint();
	}
	
	protected void endSelectRegion() {
		action = OverlayType.NONE;
		updateButtons();
		repaint();
	}
	
	private enum OverlayType {
		NONE,
		SELECT_CANVAS_REGION,
		SELECT_COLOR_REGION,
		SELECT_IMAGE_REGION,
		DRAW_IMAGE,
		GENERATE_IMAGE,
	}
	
	private enum ResizeOption {
		NONE(false, false, false, false),
		ALL(true, true, true, true),
		TOP(true, false, false, false),
		TOP_RIGHT(true, true, false, false),
		RIGHT(false, true, false, false),
		BOTTOM_RIGHT(false, true, true, false),
		BOTTOM(false, false, true, false),
		BOTTOM_LEFT(false, false, true, true),
		LEFT(false, false, false, true),
		TOP_LEFT(true, false, false, true);
		
		public final boolean top;
		public final boolean right;
		public final boolean bottom;
		public final boolean left;
		private ResizeOption(boolean top, boolean right, boolean bottom, boolean left) {
			this.top = top;
			this.right = right;
			this.bottom = bottom;
			this.left = left;
		}
	}
}
