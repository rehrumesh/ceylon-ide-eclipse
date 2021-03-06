package com.redhat.ceylon.eclipse.code.outline;

import static com.redhat.ceylon.eclipse.code.outline.HierarchyView.showHierarchyView;
import static com.redhat.ceylon.eclipse.util.EditorUtil.getCurrentEditor;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.views.contentoutline.ContentOutline;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.util.Nodes;

public class ShowInHierarchyAction extends Action implements IObjectActionDelegate {
    
    private IWorkbenchPartSite site;
    protected Declaration declaration;
    private ContentOutline outlineView;
    
    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        if (outlineView==null) return;
        try {
            final ITreeSelection oultineSelection = 
                    (ITreeSelection) outlineView.getSelection();
            CeylonOutlineNode on = 
                    (CeylonOutlineNode) oultineSelection.getFirstElement();
            if (on!=null) {
                IEditorPart currentEditor = getCurrentEditor();
                if (currentEditor instanceof CeylonEditor) {
                    CeylonParseController parseController = 
                            ((CeylonEditor) currentEditor).getParseController();
                    Tree.CompilationUnit rootNode = 
                            parseController.getRootNode();
                    if (rootNode!=null) {
                        Node node = Nodes.findNode(rootNode, 
                                on.getStartOffset());
                        if (node instanceof Tree.Declaration) {
                            declaration = 
                                    ((Tree.Declaration) node).getDeclarationModel();
                            action.setEnabled(isValidSelection());
                            return; //early exit
                        }
                    }
                }
            }
            declaration=null;
            action.setEnabled(false);
        }
        catch (Exception e) {
            action.setEnabled(false);
        }
    }

    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        outlineView = (ContentOutline) targetPart;
        site = targetPart.getSite();
    }
    
    @Override
    public void run(IAction action) {
        run();
    }
    
    @Override
    public void run() {
        if (isValidSelection()) {
            try {
                showHierarchyView().focusOn(declaration);
            }
            catch (PartInitException e) {
                e.printStackTrace();
            }
        }
        else {
            MessageDialog.openWarning(site.getShell(), 
                    "Ceylon Find Error", 
                    "No appropriate declaration name selected");
        }
    }
    
    private boolean isValidSelection() {
        return declaration!=null;
    }

}
