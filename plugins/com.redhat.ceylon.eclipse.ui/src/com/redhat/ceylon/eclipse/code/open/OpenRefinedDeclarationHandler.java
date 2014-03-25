package com.redhat.ceylon.eclipse.code.open;

import static com.redhat.ceylon.eclipse.code.editor.EditorUtil.getCurrentEditor;
import static com.redhat.ceylon.eclipse.code.editor.EditorUtil.getSelection;
import static com.redhat.ceylon.eclipse.code.editor.Navigation.gotoDeclaration;
import static com.redhat.ceylon.eclipse.util.Nodes.findNode;
import static com.redhat.ceylon.eclipse.util.Nodes.getReferencedModel;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.IEditorPart;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Referenceable;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;

public class OpenRefinedDeclarationHandler extends AbstractHandler {
    
    private Node getSelectedNode(ITextSelection textSel) {
        CeylonEditor editor = (CeylonEditor) getCurrentEditor();
        CeylonParseController pc = editor.getParseController();
        if (pc==null) {
            return null;
        }
        else {
            Tree.CompilationUnit ast = pc.getRootNode();
            if (ast == null) {
                return null;
            }
            else {
                return findNode(ast, textSel.getOffset());
            }
        }
    }
    
    public boolean isEnabled() {
        IEditorPart editor = getCurrentEditor();
        if (super.isEnabled() && editor instanceof CeylonEditor) {
            CeylonEditor ce = (CeylonEditor) editor;
            Referenceable dec = getReferencedModel(getSelectedNode(getSelection(ce)));
            if (dec instanceof Declaration) {
                return !((Declaration)dec).getRefinedDeclaration().equals(dec);
            }
            else {
                return false;
            }
        }
        else {
            return false;
        }
    }
    
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IEditorPart editor = getCurrentEditor();
        if (editor instanceof CeylonEditor) {
            CeylonEditor ce = (CeylonEditor) editor;
            Referenceable dec = getReferencedModel(getSelectedNode(getSelection(ce)));
            if (dec instanceof Declaration) {
                Declaration refined = ((Declaration) dec).getRefinedDeclaration();
                gotoDeclaration(refined, ce);
            }
        }
        return null;
    }
        
}