/*
 * Copyright (C) 2012-2018 Gregory Hedlund
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *    http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.phon.plugins.praat.export;

import ca.phon.plugins.praat.Segmentation;
import ca.phon.session.*;
import ca.phon.ui.tristatecheckbox.*;

import javax.swing.tree.*;
import java.util.*;

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
