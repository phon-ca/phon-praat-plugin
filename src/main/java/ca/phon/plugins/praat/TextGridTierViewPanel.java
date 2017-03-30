package ca.phon.plugins.praat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.jdesktop.swingx.JXTable;

import ca.hedlund.jpraat.TextGridUtils;
import ca.hedlund.jpraat.binding.fon.Function;
import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.hedlund.jpraat.exceptions.PraatException;
import ca.phon.session.Session;
import ca.phon.session.SystemTierType;
import ca.phon.session.TierViewItem;
import ca.phon.ui.CommonModuleFrame;
import ca.phon.ui.action.PhonUIAction;
import ca.phon.ui.fonts.FontPreferences;
import ca.phon.ui.layout.ButtonBarBuilder;
import ca.phon.ui.nativedialogs.MessageDialogProperties;
import ca.phon.ui.nativedialogs.NativeDialogs;
import ca.phon.util.icons.IconManager;
import ca.phon.util.icons.IconSize;

/**
 * Interface for modifying the visibility and
 * name/mapping for the given {@link TextGridView}
 *
 */
public class TextGridTierViewPanel extends JPanel {

	private final static Logger LOGGER = Logger.getLogger(TextGridTierViewPanel.class.getName());

	private TextGridView parentView;

	private JXTable tierViewTable;

	private JButton renameButton;

	private JButton moveUpButton;

	private JButton moveDownButton;

	private JButton deleteTierButton;

	private JButton mapTierButton;

	public TextGridTierViewPanel(TextGridView view) {
		super();

		this.parentView = view;

		init();
	}

	private void init() {
		setLayout(new BorderLayout());

		final ImageIcon renameIcn =
				IconManager.getInstance().getIcon("actions/edit-rename", IconSize.SMALL);
		final PhonUIAction renameAct = new PhonUIAction(this, "onRename");
		renameAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Rename selected TextGrid tier");
		renameAct.putValue(PhonUIAction.SMALL_ICON, renameIcn);
		renameButton = new JButton(renameAct);

		final ImageIcon upIcn =
				IconManager.getInstance().getIcon("actions/draw-arrow-up", IconSize.SMALL);
		final PhonUIAction moveUpAct = new PhonUIAction(this, "onMoveUp");
		moveUpAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Move selected TextGrid tier up");
		moveUpAct.putValue(PhonUIAction.SMALL_ICON, upIcn);
		moveUpButton = new JButton(moveUpAct);

		final ImageIcon downIcn =
				IconManager.getInstance().getIcon("actions/draw-arrow-down", IconSize.SMALL);
		final PhonUIAction moveDownAct = new PhonUIAction(this, "onMoveDown");
		moveDownAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Move selected TextGrid tier down");
		moveDownAct.putValue(PhonUIAction.SMALL_ICON, downIcn);
		moveDownButton = new JButton(moveDownAct);

		final ImageIcon removeIcn =
				IconManager.getInstance().getIcon("actions/list-remove", IconSize.SMALL);
		final PhonUIAction deleteTierAct = new PhonUIAction(this, "onDelete");
		deleteTierAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Delete selected TextGrid tier");
		deleteTierAct.putValue(PhonUIAction.SMALL_ICON, removeIcn);
		deleteTierButton = new JButton(deleteTierAct);

		final ImageIcon mapIcn =
				IconManager.getInstance().getIcon("actions/two-way-arrow", IconSize.SMALL);
		final PhonUIAction mapTierAct = new PhonUIAction(this, "onMapTier");
		mapTierAct.putValue(PhonUIAction.SHORT_DESCRIPTION, "Map selected TextGrid tier to Phon tier");
		mapTierAct.putValue(PhonUIAction.SMALL_ICON, mapIcn);
		mapTierButton = new JButton(mapTierAct);

		final JToolBar toolBar = new JToolBar();
		toolBar.add(mapTierButton);
		toolBar.add(renameButton);
		toolBar.addSeparator();
		toolBar.add(moveUpButton);
		toolBar.add(moveDownButton);
		toolBar.addSeparator();
		toolBar.add(deleteTierButton);

		final TextGridTableModel model = new TextGridTableModel();
		tierViewTable = new JXTable(model);
		tierViewTable.setSortable(false);
		tierViewTable.getColumn(0).setMaxWidth(40);
		tierViewTable.getColumn(2).setMaxWidth(100);
		tierViewTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		final JScrollPane scroller = new JScrollPane(tierViewTable);

		add(toolBar, BorderLayout.NORTH);
		add(scroller, BorderLayout.CENTER);
	}

	public void onMapTier() {
		if(tierViewTable.getSelectedRow() < 0) return;

		final JTree tree = new JTree(createTreeModel());
		final DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)tree.getModel().getRoot();
		final TreePath rootPath = new TreePath(rootNode);
		for(int i = 0; i < rootNode.getChildCount(); i++) {
			final TreePath treePath = rootPath.pathByAddingChild(rootNode.getChildAt(i));
			tree.expandPath(treePath);
		}

		tree.setVisibleRowCount(10);
		tree.expandPath(new TreePath(tree.getModel().getRoot()));
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		final JScrollPane scroller = new JScrollPane(tree);

		final Point p = new Point(0, mapTierButton.getHeight());
		SwingUtilities.convertPointToScreen(p, mapTierButton);

		final JFrame popup = new JFrame("Map TextGrid Tier");
		popup.setUndecorated(true);
		popup.addWindowFocusListener(new WindowFocusListener() {

			@Override
			public void windowLostFocus(WindowEvent e) {
				destroyPopup(popup);
			}

			@Override
			public void windowGainedFocus(WindowEvent e) {
			}

		});

		final PhonUIAction cancelAct = new PhonUIAction(this, "destroyPopup", popup);
		cancelAct.putValue(PhonUIAction.NAME, "Cancel");
		final JButton cancelBtn = new JButton(cancelAct);

		final PhonUIAction okAct = new PhonUIAction(this, "mapTier", tree);
		okAct.putValue(PhonUIAction.NAME, "Map to Selected Phon Tier and Dimension");
		final JButton okBtn = new JButton(okAct);
		okBtn.addActionListener( (e) -> {
			final TreePath selectedPath = tree.getSelectionPath();
			if(selectedPath != null) {
				final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)selectedPath.getLastPathComponent();
				if(treeNode.isLeaf()) {
					destroyPopup(popup);
				}
			}
		} );

		final JComponent btnBar = ButtonBarBuilder.buildOkCancelBar(okBtn, cancelBtn);

		popup.setLayout(new BorderLayout());
		popup.add(scroller, BorderLayout.CENTER);
		popup.add(btnBar, BorderLayout.SOUTH);

		popup.pack();
		popup.setLocation(p.x, p.y);
		popup.setVisible(true);

		popup.getRootPane().setDefaultButton(okBtn);
	}

	public void mapTier(JTree tree) {
		int selectedRow = tierViewTable.getSelectedRow();
		if(selectedRow < 0) return;

		final Function tgTier = parentView.getTextGrid().tier(selectedRow+1);

		final TreePath path = tree.getSelectionPath();
		if(path != null) {
			final DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode)path.getLastPathComponent();
			if(selectedNode.isLeaf()) {
				final String tierName = ((DefaultMutableTreeNode)selectedNode.getParent()).getUserObject().toString();
				final String segmentation = ((DefaultMutableTreeNode)selectedNode).getUserObject().toString();

				final String newName = tierName + ": " + segmentation;
				if(TextGridUtils.tierNumberFromName(parentView.getTextGrid(), newName) <= 0) {
					tgTier.setName(newName);
					((TextGridTableModel)tierViewTable.getModel()).fireTableCellUpdated(selectedRow, 1);

					saveTextGrid();

					parentView.setTextGrid(parentView.getTextGrid());
				}
			}
		}
	}

	private void saveTextGrid() {
		try {
			TextGridManager.saveTextGrid(parentView.getTextGrid(), parentView.getCurrentTextGridFile());
		} catch (IOException e) {
			Toolkit.getDefaultToolkit().beep();
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	public void destroyPopup(JFrame popup) {
		popup.setVisible(false);
		popup.dispose();
	}

	private TreeModel createTreeModel() {
		final Session session = parentView.getParentView().getEditor().getSession();
		final List<TierViewItem> tierOrder = session.getTierView();

		final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(session.getCorpus() + "." + session.getName());
		for(TierViewItem tierView:tierOrder) {
			if(!tierView.isVisible()) continue;

			final String tierName = tierView.getTierName();
			final SystemTierType systemTier = SystemTierType.tierFromString(tierName);

			final DefaultMutableTreeNode tierNode = new DefaultMutableTreeNode(tierName);

			for(Segmentation type:Segmentation.values()) {
				if(type == Segmentation.SYLLABLE || type == Segmentation.PHONE) {
					final boolean isIPA =
							(systemTier == SystemTierType.IPATarget) || (systemTier == SystemTierType.IPAActual);
					if(!isIPA) continue;
				}

				final String name = tierName + ": " + type.toString();
				if(TextGridUtils.tierNumberFromName(parentView.getTextGrid(), name) > 0) {
					continue;
				}

				final DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(type);
				tierNode.add(typeNode);
			}
			if(tierNode.getChildCount() > 0)
				rootNode.add(tierNode);
		}
		return new DefaultTreeModel(rootNode);
	}

	public void onRename() {
		final int selectedRow = tierViewTable.getSelectedRow();
		if(selectedRow >= 0 && selectedRow < tierViewTable.getRowCount()) {
			tierViewTable.editCellAt(selectedRow, 1);
			tierViewTable.requestFocusInWindow();
		}
	}

	public void onMoveUp() {
		final int selectedRow = tierViewTable.getSelectedRow();
		if(selectedRow > 0 && selectedRow < parentView.getTextGrid().numberOfTiers()) {
			final TextGrid tg = parentView.getTextGrid();
			final List<Function> allTiers = new ArrayList<>();
			for(int i = 1; i <= tg.numberOfTiers(); i++) {
				allTiers.add(tg.tier(i));
			}
			final Function selectedTier = parentView.getTextGrid().tier(selectedRow+1);

			int newLocation = selectedRow - 1;
			allTiers.remove(selectedRow);
			allTiers.add(newLocation, selectedTier);

			try {
				final TextGrid newTextGrid = reoderTiers(allTiers);
				parentView.setTextGrid(newTextGrid);
				saveTextGrid();

				((TextGridTableModel)tierViewTable.getModel()).fireTableRowsDeleted(selectedRow, selectedRow);
				((TextGridTableModel)tierViewTable.getModel()).fireTableRowsInserted(newLocation, newLocation);
				tierViewTable.getSelectionModel().setSelectionInterval(newLocation, newLocation);
			} catch (PraatException e) {
				Toolkit.getDefaultToolkit().beep();
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
		}
	}

	public void onMoveDown() {
		final int selectedRow = tierViewTable.getSelectedRow();
		if(selectedRow >= 0 && selectedRow < parentView.getTextGrid().numberOfTiers()-1) {
			final TextGrid tg = parentView.getTextGrid();
			final List<Function> allTiers = new ArrayList<>();
			for(int i = 1; i <= tg.numberOfTiers(); i++) {
				allTiers.add(tg.tier(i));
			}
			final Function selectedTier = parentView.getTextGrid().tier(selectedRow+1);

			int newLocation = selectedRow + 1;
			allTiers.remove(selectedRow);
			allTiers.add(newLocation, selectedTier);

			try {
				final TextGrid newTextGrid = reoderTiers(allTiers);
				parentView.setTextGrid(newTextGrid);
				saveTextGrid();

				((TextGridTableModel)tierViewTable.getModel()).fireTableRowsDeleted(selectedRow, selectedRow);
				((TextGridTableModel)tierViewTable.getModel()).fireTableRowsInserted(newLocation, newLocation);
				tierViewTable.getSelectionModel().setSelectionInterval(newLocation, newLocation);
			} catch (PraatException e) {
				Toolkit.getDefaultToolkit().beep();
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
		}
	}

	private TextGrid reoderTiers(List<Function> tiers) throws PraatException {
		final TextGrid tg = TextGrid.createWithoutTiers(parentView.getTextGrid().getXmin(), parentView.getTextGrid().getXmax());
		for(Function tier:tiers) {
			tg.addTier(tier);
		}
		return tg;
	}

	public void onDelete() {
		final int selectedRow = tierViewTable.getSelectedRow();
		if(selectedRow >= 0 && selectedRow < tierViewTable.getRowCount()) {
			final Function tier = parentView.getTextGrid().tier(selectedRow+1);

			final MessageDialogProperties props = new MessageDialogProperties();
			props.setParentWindow(CommonModuleFrame.getCurrentFrame());
			props.setTitle("Delete TextGrid Tier");
			props.setHeader("Delete TextGrid Tier");
			props.setMessage("Delete TextGrid tier named " + tier.getName() + "? This cannot be undone.");
			props.setOptions(MessageDialogProperties.okCancelOptions);
			props.setRunAsync(false);

			final int r = NativeDialogs.showMessageDialog(props);
			if(r == 0) {
				try {
					parentView.getTextGrid().removeTier(selectedRow+1);

					((TextGridTableModel)tierViewTable.getModel()).fireTableRowsDeleted(selectedRow, selectedRow);

					saveTextGrid();

					parentView.setTextGrid(parentView.getTextGrid());
				} catch (PraatException e) {
					Toolkit.getDefaultToolkit().beep();
					LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
				}
			}
		}
	}

	private class TextGridTableModel extends AbstractTableModel {

		@Override
		public int getRowCount() {
			return (int)parentView.getTextGrid().numberOfTiers();
		}

		@Override
		public int getColumnCount() {
			return 3;
		}

		@Override
		public String getColumnName(int col) {
			switch(col) {
			case 0:
				return "Show";

			case 1:
				return "Name";

			case 2:
				return "Type";

			default:
				return super.getColumnName(col);
			}
		}

		@Override
		public Class<?> getColumnClass(int col) {
			switch(col) {
			case 0:
				return Boolean.class;

			case 1:
				return String.class;

			case 2:
				return String.class;

			default:
				return super.getColumnClass(col);
			}
		}

		@Override
		public boolean isCellEditable(int row, int col) {
			switch(col) {
			case 0:
				return true;

			case 1:
				return true;

			case 2:
				return false;

			default:
				return super.isCellEditable(row, col);
			}
		}

		@Override
		public void setValueAt(Object value, int rowIndex, int columnIndex) {
			final Function tier = parentView.getTextGrid().tier(rowIndex+1);
			if(columnIndex == 0) {
				final Boolean show = Boolean.parseBoolean(value.toString());
				parentView.getTextGridPainter().setHidden(tier.getName(), !show);
				parentView.saveHiddenTiers();

				parentView.setTextGrid(parentView.getTextGrid());
			} else if(columnIndex == 1) {
				final String newName = value.toString();
				if(newName.trim().length() > 0) {
					// make sure TextGrid does not have a tier with same name
					if(TextGridUtils.tierNumberFromName(parentView.getTextGrid(), newName) <= 0) {
						tier.setName(newName);
						saveTextGrid();

						parentView.setTextGrid(parentView.getTextGrid());
					}
				}
			}
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			final Function tier = parentView.getTextGrid().tier(rowIndex+1);

			if(columnIndex == 0) {
				return !parentView.getTextGridPainter().isHidden(tier.getName());
			} else if(columnIndex == 1) {
				return tier.getName();
			} else if(columnIndex == 2) {
				try {
					parentView.getTextGrid().checkSpecifiedTierIsIntervalTier(rowIndex+1);
					return "Interval";
				} catch (PraatException e) {
					return "Point";
				}
			} else {
				return null;
			}
		}

	}

}
