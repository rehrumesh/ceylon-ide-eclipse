/*******************************************************************************
* Copyright (c) 2007 IBM Corporation.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*    Robert Fuhrer (rfuhrer@watson.ibm.com) - initial API and implementation
*******************************************************************************/

package com.redhat.ceylon.eclipse.code.outline;

import static com.redhat.ceylon.eclipse.code.outline.CeylonOutlineNode.DEFAULT_CATEGORY;
import static com.redhat.ceylon.eclipse.code.outline.CeylonOutlineNode.IMPORT_LIST_CATEGORY;
import static com.redhat.ceylon.eclipse.ui.CeylonPlugin.PLUGIN_ID;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.CONFIG_LABELS;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.EXPAND_ALL;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.HIDE_PRIVATE;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.SORT_ALPHA;
import static com.redhat.ceylon.eclipse.util.EditorUtil.getCurrentEditor;
import static com.redhat.ceylon.eclipse.util.EditorUtil.getPreferences;
import static org.eclipse.ui.PlatformUI.getWorkbench;
import static org.eclipse.ui.dialogs.PreferencesUtil.createPreferenceDialogOn;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.CollapseAllAction;
import org.eclipse.jdt.internal.ui.packageview.DefaultElementComparer;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.DecoratingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DecorationContext;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.custom.CaretEvent;
import org.eclipse.swt.custom.CaretListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;

import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.SyntheticVariable;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewer;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.code.parse.TreeLifecycleListener;
import com.redhat.ceylon.eclipse.code.preferences.CeylonPreferencePage;
import com.redhat.ceylon.eclipse.core.model.CeylonUnit;
import com.redhat.ceylon.eclipse.ui.CeylonPlugin;

public class CeylonOutlinePage extends ContentOutlinePage 
        implements TreeLifecycleListener, CaretListener {
    
    private static final ImageRegistry imageRegistry = 
            CeylonPlugin.getInstance().getImageRegistry();

    private static final String OUTLINE_POPUP_MENU_ID = 
            PLUGIN_ID + ".outline.popupMenu";
    
    private static final ImageDescriptor PUBLIC = 
            imageRegistry.getDescriptor(HIDE_PRIVATE);
    private static final ImageDescriptor ALPHA = 
            imageRegistry.getDescriptor(SORT_ALPHA);
    
    private final ITreeContentProvider contentProvider = 
            new OutlineContentProvider();
    private StyledCellLabelProvider labelProvider;
    private CeylonParseController parseController;
    private CeylonSourceViewer sourceViewer;
    
    private volatile boolean suspend = false;
    
    private IPropertyChangeListener propertyChangeListener;
    
    @Override
    public void init(IPageSite site) {
        super.init(site);
        propertyChangeListener = new IPropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                getTreeViewer().refresh();
            }
        };
        getPreferences().addPropertyChangeListener(propertyChangeListener);
    }
    
    public CeylonOutlinePage(CeylonParseController parseController,
            CeylonSourceViewer sourceViewer) {
        this.parseController = parseController;
        this.sourceViewer = sourceViewer;
    }
    
    @Override
    public Stage getStage() {
        return Stage.FOR_OUTLINE;
    }
    
    @Override
    public void update(final CeylonParseController controller, 
            IProgressMonitor monitor) {
        TreeViewer treeViewer = getTreeViewer();
        if (treeViewer!=null && 
                !treeViewer.getTree().isDisposed()) {
            treeViewer.getTree().getDisplay()
                   .syncExec(new Runnable() {
                @Override
                public void run() {
                    TreeViewer viewer = getTreeViewer();
                    if (viewer!=null && 
                            !viewer.getTree().isDisposed()) {
                        viewer.getTree().setRedraw(false);
                        try {
                            boolean noInput = viewer.getInput()==null;
                            Object[] expanded = viewer.getExpandedElements();
                            if (controller.getStage().ordinal() >= getStage().ordinal()) {
                                CeylonOutlineNode rootNode = 
                                        new CeylonOutlineBuilder()
                                            .buildTree(controller);
                                viewer.setInput(rootNode);
                                if (noInput) {
                                    expand(viewer, rootNode);
                                }
                                else {
                                    for (Object obj: expanded) {
                                        viewer.expandToLevel(obj, 1);
                                    }
                                    expandCaretedNode(sourceViewer.getSelectedRange().x);
                                }
                            }
                        }
                        finally {
                            viewer.getTree().setRedraw(true);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void createControl(Composite parent) {
        super.createControl(parent);
        TreeViewer viewer = getTreeViewer();
        if (labelProvider!=null) {
            labelProvider.dispose();
        }
        labelProvider = createLabelProvider();
        viewer.setLabelProvider(labelProvider);
        viewer.setContentProvider(contentProvider);
        viewer.setComparer(new DefaultElementComparer());
        CeylonOutlineNode rootNode = 
                new CeylonOutlineBuilder()
                        .buildTree(parseController);
        viewer.setInput(rootNode);
        expand(viewer, rootNode);
        sourceViewer.getTextWidget().addCaretListener(this);
    }

    private DecoratingStyledCellLabelProvider createLabelProvider() {
        return new DecoratingStyledCellLabelProvider(new CeylonLabelProvider(), 
                getWorkbench().getDecoratorManager().getLabelDecorator(), 
                DecorationContext.DEFAULT_CONTEXT);
    }
    
    @Override
    public void makeContributions(IMenuManager menuManager,
            IToolBarManager toolBarManager, 
            IStatusLineManager statusLineManager) {
        super.makeContributions(menuManager, toolBarManager, 
                statusLineManager);
        MenuManager contextMenu = new MenuManager();
        JavaPlugin.createStandardGroups(contextMenu);        
        TreeViewer treeViewer = getTreeViewer();
        getSite().registerContextMenu(OUTLINE_POPUP_MENU_ID, 
                contextMenu, treeViewer);
        Control control = treeViewer.getControl();
        control.setMenu(contextMenu.createContextMenu(control));
        ExpandAllAction expandAction = new ExpandAllAction();
        CollapseAllAction collapseAllAction = new CollapseAllAction(treeViewer);
        LexicalSortingAction sortAction = new LexicalSortingAction();
        HideNonSharedAction hideAction = new HideNonSharedAction();
        toolBarManager.add(expandAction);
        toolBarManager.add(collapseAllAction);
        toolBarManager.add(sortAction);
        toolBarManager.add(hideAction);
        menuManager.add(expandAction);
        menuManager.add(collapseAllAction);
        menuManager.add(new Separator());
        menuManager.add(sortAction);
        menuManager.add(hideAction);
        menuManager.add(new Separator());
        Action configureAction =
                new Action("Configure Labels...", 
                        CeylonPlugin.getInstance().getImageRegistry()
                                .getDescriptor(CONFIG_LABELS)) {
            @Override
            public void run() {
                createPreferenceDialogOn(getSite().getShell(), 
                        CeylonPreferencePage.ID, 
                        new String[] { CeylonPreferencePage.ID }, 
                        null).open();
            }
        };
        menuManager.add(configureAction);
    }
    
    @Override
    public void dispose() {
        super.dispose();
        if (labelProvider!=null) {
            labelProvider.dispose();
            labelProvider = null;
        }
        if (propertyChangeListener!=null) {
            getPreferences().removePropertyChangeListener(propertyChangeListener);
            propertyChangeListener = null;
        }
        sourceViewer.getTextWidget().removeCaretListener(this);
        sourceViewer = null;
        parseController = null;
    }

    static void expand(TreeViewer viewer, CeylonOutlineNode rootNode) {
        if (rootNode!=null) {
            for (CeylonOutlineNode on: rootNode.getChildren()) {
                if (on.getCategory()==IMPORT_LIST_CATEGORY) {
                    viewer.collapseToLevel(on, 
                            TreeViewer.ALL_LEVELS);
                }
                else {
                    viewer.expandToLevel(on, 
                            TreeViewer.ALL_LEVELS);
                }
            }
        }
    }
    
    private static final class OutlineContentProvider 
            implements ITreeContentProvider {
        @Override
        public Object[] getChildren(Object element) {
            return ((CeylonOutlineNode) element).getChildren().toArray();
        }
        @Override
        public Object getParent(Object element) {
            return ((CeylonOutlineNode) element).getParent();
        }
        @Override
        public boolean hasChildren(Object element) {
            Object[] children= getChildren(element);
            return children!=null && children.length > 0;
        }
        @Override
        public Object[] getElements(Object inputElement) {
            return getChildren(inputElement);
        }
        @Override
        public void dispose() {}
        @Override
        public void inputChanged(Viewer viewer, 
                Object oldInput, Object newInput) {}
    }

    private class ExpandAllAction extends Action {

        private ExpandAllAction() {
            super("Expand All");
            setToolTipText("Expand All");
            
            ImageDescriptor desc = imageRegistry
                    .getDescriptor(EXPAND_ALL);
            setHoverImageDescriptor(desc);
            setImageDescriptor(desc);
        }

        @Override
        public void run() {
            TreeViewer outlineViewer = getTreeViewer();
            if (outlineViewer != null) {
                outlineViewer.expandAll();
            }
        }
        
    }

    private static final ViewerComparator fElementComparator = 
            new ViewerComparator() {
        @Override
        public int compare(Viewer viewer, Object e1, Object e2) {
            CeylonOutlineNode t1 = (CeylonOutlineNode) e1;
            CeylonOutlineNode t2 = (CeylonOutlineNode) e2;
            int cat1 = t1.getCategory();
            int cat2 = t2.getCategory();
            if (cat1 == cat2) {
                String i1 = t1.getIdentifier();
                String i2 = t2.getIdentifier();
                if (i1==i2) return 0;
                if (i1==null) return -1;
                if (i2==null) return 1;
                return i1.compareTo(i2);
            }
            return cat1 - cat2;
        }
    };
    
    private static final ViewerComparator fPositionComparator = 
            new ViewerComparator() {
        @Override
        public int compare(Viewer viewer, Object e1, Object e2) {
            CeylonOutlineNode t1 = (CeylonOutlineNode) e1;
            CeylonOutlineNode t2 = (CeylonOutlineNode) e2;
            return t1.getStartOffset() - t2.getStartOffset();
        }
    };

    private class LexicalSortingAction extends Action {
        
        private LexicalSortingAction() {
            setText("Sort");
            setToolTipText("Sort by Name");
            setDescription("Sort entries lexically by name");
            
            this.setHoverImageDescriptor(ALPHA);
            this.setImageDescriptor(ALPHA); 
            
            boolean checked = getPreferences()
                    .getBoolean("sortOutlineViewByName");
            valueChanged(checked, false);
        }

        @Override
        public void run() {
            valueChanged(isChecked(), true);
        }

        private void valueChanged(final boolean on, boolean store) {
            final TreeViewer outlineViewer = getTreeViewer();
            setChecked(on);
            Display display = 
                    outlineViewer.getControl().getDisplay();
            BusyIndicator.showWhile(display, 
                    new Runnable() {
                @Override
                public void run() {
                    outlineViewer.setComparator(on?fElementComparator:fPositionComparator);
                }
            });

            if (store) {
                getPreferences()
                    .setValue("sortOutlineViewByName", on);
            }
        }
    }
    
    private static final ViewerFilter filter = new ViewerFilter() {
        @Override
        public boolean select(Viewer viewer, 
                Object parentElement, Object element) {
            return ((CeylonOutlineNode) element).isShared();
        }
    };

    private class HideNonSharedAction extends Action {
        
        public HideNonSharedAction() {
            setText("Hide Unshared");
            setToolTipText("Hide Unshared Declarations");
            setDescription("Hide unshared declarations");
            
            setHoverImageDescriptor(PUBLIC);
            setImageDescriptor(PUBLIC); 
            
            boolean checked = getPreferences()
                    .getBoolean("hideNonSharedInOutlineView");
            valueChanged(checked, false);
        }

        @Override
        public void run() {
            valueChanged(isChecked(), true);
        }

        private void valueChanged(final boolean on, boolean store) {
            final TreeViewer outlineViewer = getTreeViewer();
            setChecked(on);
            Display display = 
                    outlineViewer.getControl().getDisplay();
            BusyIndicator.showWhile(display, 
                    new Runnable() {
                @Override
                public void run() {
                    if (on) {
                        outlineViewer.addFilter(filter);
                    }
                    else {
                        outlineViewer.removeFilter(filter);
                    }
                }
            });
            
            if (store) {
                getPreferences()
                    .setValue("hideNonSharedInOutlineView", on);
            }
        }
    }
    
    @Override
    public void selectionChanged(SelectionChangedEvent event) {
        super.selectionChanged(event);
        if (!suspend) {
            ITreeSelection sel = 
                    (ITreeSelection) event.getSelection();
            if (!sel.isEmpty()) {
                CeylonOutlineNode on = 
                        (CeylonOutlineNode) sel.getFirstElement();
                if (on.getCategory()==DEFAULT_CATEGORY) {
                    suspend = true;
                    try {
                        int startOffset = on.getStartOffset();
                        int endOffset = on.getEndOffset();
                        ((ITextEditor) getCurrentEditor())
                                .selectAndReveal(startOffset, 
                                        endOffset-startOffset);
                    }
                    finally {
                        suspend = false;
                    }
                }
            }
        }
    }

    @Override
    public void caretMoved(CaretEvent event) {
        expandCaretedNode(sourceViewer.widgetOffset2ModelOffset(event.caretOffset));
    }

    private void expandCaretedNode(int offset) {
        if (suspend) return;
        if (offset==0) return; //right at the start of file, don't expand the import list
        CompilationUnit rootNode = 
                parseController.getRootNode();
        if (rootNode==null || rootNode.getUnit()==null) {
            return;
        }
        if (rootNode.getUnit() instanceof CeylonUnit) {
            CeylonUnit ceylonUnit = 
                    (CeylonUnit) rootNode.getUnit();
            PhasedUnit phasedUnit = 
                    ceylonUnit.getPhasedUnit();
            if (phasedUnit==null || ! phasedUnit.isFullyTyped()) {
                return;
            }
        } 
        if (getTreeViewer().getInput()==null) return;
        suspend = true;
        try {
            OutlineNodeVisitor visitor = 
                    new OutlineNodeVisitor(offset);
            rootNode.visit(visitor);
            List<CeylonOutlineNode> result = visitor.result;
            if (!result.isEmpty()) {
                TreePath treePath = new TreePath(result.toArray());
                CeylonOutlineNode last = result.get(result.size()-1);
                if (!last.getChildren().isEmpty()) {
                    getTreeViewer().expandToLevel(treePath, 1);
                }
                setSelection(new TreeSelection(treePath));
            }
        }
        finally {
            suspend = false;
        }
    }
    
    private static final class OutlineNodeVisitor extends Visitor {
        private final int offset;
        private final boolean hideNonshared;
        OutlineNodeVisitor(int offset) {
            this.offset = offset;
            hideNonshared = getPreferences()
                    .getBoolean("hideNonSharedInOutlineView");
        }
        List<CeylonOutlineNode> result = 
                new ArrayList<CeylonOutlineNode>();
        private CeylonOutlineNode getParent() {
            return result.isEmpty() ? 
                    null : result.get(result.size()-1);
        }
        @Override
        public void visit(Tree.Declaration that) {
            if (!(that instanceof Tree.TypeParameterDeclaration) &&
                !(that instanceof Tree.TypeConstraint) &&
                !(that instanceof Tree.Variable && 
                            ((Tree.Variable) that).getType() 
                                    instanceof SyntheticVariable)) {
                if (inBounds(that)) {
                    Declaration dm = that.getDeclarationModel();
                    if (!hideNonshared||dm!=null&&dm.isShared()) {
                        result.add(new CeylonOutlineNode(that, 
                                getParent()));
                        super.visit(that);
                    }
                }
                else {
                    super.visit(that);
                }
            }
            else {
                super.visit(that);
            }
        }
        @Override
        public void visit(Tree.SpecifierStatement that) {
            Tree.Term bme = that.getBaseMemberExpression();
            if (that.getRefinement() &&
                    (bme instanceof Tree.BaseMemberExpression ||
                    bme instanceof Tree.ParameterizedExpression &&
                        ((Tree.ParameterizedExpression) bme).getPrimary() 
                                instanceof Tree.BaseMemberExpression)) {
                if (inBounds(that)) {
                    if (!hideNonshared) {
                        result.add(new CeylonOutlineNode(that, 
                                getParent()));
                    }
                    else {
                        super.visit(that);
                    }
                }
                else {
                    super.visitAny(that);
                }
            }
        }        
        @Override
        public void visit(Tree.Parameter that) {}
        @Override
        public void visit(Tree.CompilationUnit that) {
//            result.add(new CeylonOutlineNode(that, ROOT_CATEGORY));
            super.visit(that);
        }
        @Override
        public void visit(Tree.ImportList that) {
            if (inBounds(that)) {
                result.add(new CeylonOutlineNode(that, 
                        IMPORT_LIST_CATEGORY));
            }
            super.visit(that);
        }
        @Override
        public void visit(Tree.PackageDescriptor that) {
            if (inBounds(that)) {
                result.add(new CeylonOutlineNode(that));
            }
            super.visit(that);
        }
        @Override
        public void visit(Tree.ModuleDescriptor that) {
            if (inBounds(that)) {
                result.add(new CeylonOutlineNode(that));
            }
            super.visit(that);
        }
        @Override
        public void visit(Tree.Import that) {
            if (inBounds(that)) {
                result.add(new CeylonOutlineNode(that, 
                        getParent()));
            }
            super.visit(that);
        }
        @Override
        public void visit(Tree.ImportModule that) {
            if (inBounds(that)) {
                result.add(new CeylonOutlineNode(that, 
                        getParent()));
            }
            super.visit(that);
        }
        private boolean inBounds(Node that) {
            Integer tokenStartIndex = that.getStartIndex();
            Integer tokenStopIndex = that.getStopIndex();
            return tokenStartIndex!=null && tokenStopIndex!=null &&
                    tokenStartIndex<=offset && tokenStopIndex+1>=offset;
        }
    }
    
}

