/*******************************************************************************
 * Copyright (c) 2005, 2007 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.beans.ui.navigator.actions;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.navigator.ICommonActionExtensionSite;
import org.springframework.ide.eclipse.beans.ui.BeansUIPlugin;
import org.springframework.ide.eclipse.ui.navigator.actions.AbstractNavigatorAction;

/**
 * {@link Action} implementation that wraps {@link OpenConfigFileAction} and
 * {@link OpenJavaElementAction} and gets installed as global double click
 * action.
 * 
 * @author Christian Dupuis
 * @since 2.0
 */
public class OpenActionWrapperAction extends AbstractNavigatorAction {

	private OpenConfigFileAction openConfigFileAction;

	private OpenJavaElementAction openJavaElementAction;

	private Action action;

	public OpenActionWrapperAction(ICommonActionExtensionSite site,
			OpenConfigFileAction openConfigFileAction,
			OpenJavaElementAction openJavaElementAction) {
		super(site);
		this.openConfigFileAction = openConfigFileAction;
		this.openJavaElementAction = openJavaElementAction;
		setText("Op&en"); // TODO externalize text
	}

	public boolean isEnabled(IStructuredSelection selection) {
		if (selection.size() == 1) {
			if (this.openJavaElementAction.isEnabled(selection)) {
				if (shouldOpenConfigFile()) {
					this.action = openConfigFileAction;
					return openConfigFileAction.isEnabled(selection);
				}
				else {
					this.action = openJavaElementAction;
					return openJavaElementAction.isEnabled(selection);
				}
			}
			else {
				this.action = openConfigFileAction;
				return openConfigFileAction.isEnabled(selection);
			}
		}
		return false;
	}

	@Override
	public void run() {
		this.action.run();
	}

	private boolean shouldOpenConfigFile() {
		IScopeContext context = new InstanceScope();
		IEclipsePreferences node = context.getNode(BeansUIPlugin.PLUGIN_ID);
		return node.getBoolean(
				BeansUIPlugin.DEFAULT_DOUBLE_CLICK_ACTION_PREFERENCE_ID, true);
	}

}