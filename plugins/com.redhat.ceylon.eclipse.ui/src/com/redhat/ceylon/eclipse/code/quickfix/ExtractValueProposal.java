package com.redhat.ceylon.eclipse.code.quickfix;

import java.util.Collection;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider;
import com.redhat.ceylon.eclipse.code.refactor.ExtractValueRefactoringAction;

class ExtractValueProposal implements ICompletionProposal {

    private ExtractValueRefactoringAction action;
    
    public ExtractValueProposal(CeylonEditor editor) {
        action = new ExtractValueRefactoringAction(editor);
    }
    
    @Override
    public Point getSelection(IDocument doc) {
    	return null;
    }

    @Override
    public Image getImage() {
    	return CeylonLabelProvider.CHANGE;
    }

    @Override
    public String getDisplayString() {
    	return "Extract value";
    }

    @Override
    public IContextInformation getContextInformation() {
    	return null;
    }

    @Override
    public String getAdditionalProposalInfo() {
    	return null;
    }

    @Override
    public void apply(IDocument doc) {
        action.run();
    }
    
    boolean isEnabled() {
        return action.isEnabled();
    }
    
    public static void add(Collection<ICompletionProposal> proposals, CeylonEditor editor) {
        ExtractValueProposal prop = new ExtractValueProposal(editor);
        if (prop.isEnabled()) {
            proposals.add(prop);
        }
    }

}