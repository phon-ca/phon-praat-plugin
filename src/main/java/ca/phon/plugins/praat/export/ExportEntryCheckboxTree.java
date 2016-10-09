/*
 * phon-textgrid-plugin
 * Copyright (C) 2015, Gregory Hedlund <ghedlund@mun.ca>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ca.phon.plugins.praat.export;

import java.util.ArrayList;
import java.util.List;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import ca.phon.plugins.praat.Segmentation;
import ca.phon.session.Session;
import ca.phon.session.SystemTierType;
import ca.phon.session.TierViewItem;
import ca.phon.ui.tristatecheckbox.TristateCheckBoxState;
import ca.phon.ui.tristatecheckbox.TristateCheckBoxTree;
import ca.phon.ui.tristatecheckbox.TristateCheckBoxTreeNode;

public class ExportEntryCheckboxTree extends TristateCheckBoxTree {

	private static final long serialVersionUID = -3255582087058770263L;

	/**
	 * Root node
	 */
	private TristateCheckBoxTreeNode rootNode;
	
	private Session session;
	
	public ExportEntryCheckboxTree() {
		super(new TristateCheckBoxTreeNode());
		
		rootNode = (TristateCheckBoxTreeNode)super.getModel().getRoot();
	}
	
	public ExportEntryCheckboxTree(Session session) {
		super(new TristateCheckBoxTreeNode());
		
		rootNode = (TristateCheckBoxTreeNode)super.getModel().getRoot();
		if(session != null)
			setSession(session);
	}
	
	public void setSession(Session session) {
		this.session = session;
		initTree();
	}
	
	public Session getSession() {
		return this.session;
	}
	
	private void initTree() {
		createTree();
		super.setRootVisible(false);
		super.expandPath(new TreePath(rootNode));
	}
	
	private void createTree() {
		final List<TierViewItem> tierOrder = session.getTierView();
		
		for(TierViewItem tierView:tierOrder) {
			if(!tierView.isVisible()) continue;
			
			final String tierName = tierView.getTierName();
			final SystemTierType systemTier = SystemTierType.tierFromString(tierName);
			
			final TristateCheckBoxTreeNode tierNode = new TristateCheckBoxTreeNode(tierName);
			tierNode.setEnablePartialCheck(false);
			rootNode.add(tierNode);
			
			// setup export entries
			for(Segmentation type:Segmentation.values()) {
				if(type == Segmentation.SYLLABLE || type == Segmentation.PHONE) {
					final boolean isIPA = 
							(systemTier == SystemTierType.IPATarget) || (systemTier == SystemTierType.IPAActual);
					if(!isIPA) continue;
				}
				
				final TristateCheckBoxTreeNode typeNode = new TristateCheckBoxTreeNode(type);
				typeNode.setEnablePartialCheck(false);
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
			final TristateCheckBoxTreeNode exportNode = nodeForEntry(export);
			if(exportNode != null) {
				final TreePath tp = new TreePath(new Object[]{ rootNode, exportNode.getParent(), exportNode});
				setCheckingStateForPath(tp, TristateCheckBoxState.CHECKED);
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
	public TristateCheckBoxTreeNode nodeForEntry(TextGridExportEntry entry) {
		final String tierName = entry.getPhonTier();
		final Segmentation type = entry.getExportType();
		
		// find tier
		TristateCheckBoxTreeNode tierNode = null;
		for(int i = 0; i < rootNode.getChildCount(); i++) {
			final TristateCheckBoxTreeNode cNode = (TristateCheckBoxTreeNode)rootNode.getChildAt(i);
			if(cNode.getUserObject().toString().equals(tierName)) {
				tierNode = cNode;
				break;
			}
		}
		
		if(tierNode == null) return null;
		
		// now type
		for(int i = 0; i < tierNode.getChildCount(); i++) {
			final TristateCheckBoxTreeNode cNode = (TristateCheckBoxTreeNode)tierNode.getChildAt(i);
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
				if(isPathChecked(tp)) {
					final String tierName = tierNode.toString();
					final Segmentation type = (Segmentation) ((TristateCheckBoxTreeNode)typeNode).getUserObject();
					final String tgTier = tierName + ": " + type;
					
					final TextGridExportEntry entry = new TextGridExportEntry(tierName, type, tgTier);
					retVal.add(entry);
				}
				
			}
		}
		
		return retVal;
	}
}
