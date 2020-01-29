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
package ca.phon.plugins.praat;

import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;

import ca.hedlund.jpraat.TextGridUtils;
import ca.hedlund.jpraat.binding.fon.TextGrid;
import ca.phon.session.Session;
import ca.phon.session.SystemTierType;
import ca.phon.session.Tier;
import ca.phon.session.TierViewItem;

/**
 * Utility methods for mapping {@link TextGrid} tiers to Phon {@link Tier}s.
 */
public class TextGridTierMapper {

	private Session session;
	
	private TextGrid textGrid;
	
	public TextGridTierMapper(Session session, TextGrid textGrid) {
		super();
		this.session = session;
		this.textGrid = textGrid;
	}
	
	public Session getSession() {
		return session;
	}

	public void setSession(Session session) {
		this.session = session;
	}

	public TextGrid getTextGrid() {
		return textGrid;
	}

	public void setTextGrid(TextGrid textGrid) {
		this.textGrid = textGrid;
	}

	/**
	 * Create a {@link TreeModel} for selecting a new tier mapping given
	 * a {@link Session} and {@link TextGrid}
	 * 
	 * @param session
	 * @param textGrid
	 * @return treeModel
	 */
	public TreeModel createTreeModel() {
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
				if(TextGridUtils.tierNumberFromName(textGrid, name) > 0) {
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
	
}
