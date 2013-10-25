package ca.phon.plugins.praat;

import java.util.ArrayList;
import java.util.List;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import ca.phon.application.transcript.ITierOrderItem;
import ca.phon.application.transcript.ITranscript;
import ca.phon.gui.components.CheckedTreeNode;
import ca.phon.gui.components.CheckedTreeNode.SelectionMode;
import ca.phon.gui.recordeditor.SystemTierType;
import it.cnr.imaa.essi.lablib.gui.checkboxtree.CheckboxTree;

public class ExportEntryCheckboxTree extends CheckboxTree {

	private static final long serialVersionUID = -3255582087058770263L;

	/**
	 * Root node
	 */
	private CheckedTreeNode rootNode;
	
	private final ITranscript session;
	
	public ExportEntryCheckboxTree(ITranscript session) {
		super(new CheckedTreeNode());
		
		rootNode = (CheckedTreeNode)super.getModel().getRoot();
		this.session = session;
		
		initTree();
	}
	
	private void initTree() {
		createTree();
		super.setRootVisible(false);
		super.expandPath(new TreePath(rootNode));
	}
	
	private void createTree() {
		final List<ITierOrderItem> tierOrder = session.getTierView();
		
		for(ITierOrderItem tierView:tierOrder) {
			if(!tierView.isVisible()) continue;
			
			final String tierName = tierView.getTierName();
			final SystemTierType systemTier = SystemTierType.tierFromString(tierName);
			
			final CheckedTreeNode tierNode = new CheckedTreeNode(tierName);
			rootNode.add(tierNode);
			
			// setup export entries
			for(Segmentation type:Segmentation.values()) {
				if(type == Segmentation.SYLLABLE || type == Segmentation.PHONE) {
					final boolean isIPA = 
							(systemTier == SystemTierType.IPATarget) || (systemTier == SystemTierType.IPAActual);
					if(!isIPA) continue;
				}
				
				final CheckedTreeNode typeNode = new CheckedTreeNode(type);
				tierNode.add(typeNode);
			}
		}
	
	}
	
	/**
	 * Setup checked values
	 * 
	 * @param exports
	 */
	public void setChecked(List<TextGridExportEntry> exports) {
		for(TextGridExportEntry export:exports) {
			final CheckedTreeNode exportNode = nodeForEntry(export);
			if(exportNode != null) {
				final TreePath tp = new TreePath(new Object[]{ rootNode, exportNode.getParent(), exportNode});
				getCheckingModel().addCheckingPath(tp);
				expandPath(tp.getParentPath());
			}
		}
	}
	
	/**
	 * Find the tree node for the given entry
	 * 
	 * @param entry
	 * @return tree node or <code>null</code> if not found
	 */
	public CheckedTreeNode nodeForEntry(TextGridExportEntry entry) {
		final String tierName = entry.getPhonTier();
		final Segmentation type = entry.getExportType();
		
		// find tier
		CheckedTreeNode tierNode = null;
		for(int i = 0; i < rootNode.getChildCount(); i++) {
			final CheckedTreeNode cNode = (CheckedTreeNode)rootNode.getChildAt(i);
			if(cNode.getUserObject().toString().equals(tierName)) {
				tierNode = cNode;
				break;
			}
		}
		
		if(tierNode == null) return null;
		
		// now type
		for(int i = 0; i < tierNode.getChildCount(); i++) {
			final CheckedTreeNode cNode = (CheckedTreeNode)tierNode.getChildAt(i);
			if(cNode.getUserObject() == type) {
				return cNode;
			}
		}
		
		return null;
	}
	
	/**
	 * Get the selected exports
	 * 
	 * @return list of selected exports
	 */
	public List<TextGridExportEntry> getSelectedExports() {
		final List<TextGridExportEntry> retVal = new ArrayList<TextGridExportEntry>();
		
		
		for(int i = 0; i < rootNode.getChildCount(); i++) {
			final TreeNode tierNode = rootNode.getChildAt(i);
			
			for(int j = 0; j < tierNode.getChildCount(); j++) {
				final TreeNode typeNode = tierNode.getChildAt(j);
				
				final TreePath tp = new TreePath(new Object[]{ rootNode, tierNode, typeNode });
				if(getCheckingModel().isPathChecked(tp)) {
					final String tierName = tierNode.toString();
					final Segmentation type = (Segmentation) ((CheckedTreeNode)typeNode).getUserObject();
					final String tgTier = tierName + ": " + type;
					
					final TextGridExportEntry entry = new TextGridExportEntry(tierName, type, tgTier);
					retVal.add(entry);
				}
				
			}
		}
		
		return retVal;
	}
}
