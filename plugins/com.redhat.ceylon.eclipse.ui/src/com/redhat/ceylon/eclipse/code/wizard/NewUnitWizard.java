package com.redhat.ceylon.eclipse.code.wizard;

import static com.redhat.ceylon.eclipse.code.editor.Navigation.gotoLocation;
import static com.redhat.ceylon.eclipse.code.wizard.WizardUtil.runOperation;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.wizards.newresource.BasicNewResourceWizard;

import com.redhat.ceylon.eclipse.code.editor.RecentFilesPopup;
import com.redhat.ceylon.eclipse.ui.CeylonPlugin;

public class NewUnitWizard extends Wizard implements INewWizard {
    
    private IStructuredSelection selection;
    private IWorkbench workbench;
    private String pastedText;

    private NewUnitWithDeclarationWizardPage page;
    private String unitName;
    private String title;
    private String description;
    private String importText;
    
    public NewUnitWizard() {
        setDialogSettings(CeylonPlugin.getInstance().getDialogSettings());
        setWindowTitle("New Ceylon Source File");
    }
    
    public void setPastedText(String pastedText) {
        this.pastedText = pastedText;
    }
    
    public void setImports(String importText) {
        this.importText = importText;
    }
    
    public void setDefaultUnitName(String name) {
        unitName = name;
    }
    
    public void setTitleAndDescription(String title, String description) {
        this.title = title;
        this.description = description;
    }
    
    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        this.selection = selection;
        this.workbench = workbench;
    }
    
    @Override
    public boolean performFinish() {
        CreateSourceFileOperation op = 
                new CreateSourceFileOperation(page.getSourceDir(),
                        page.getPackageFragment(), page.getUnitName(),
                        page.isIncludePreamble(), getDeclarationText());
        if (runOperation(op, getContainer())) {
            IFile file = op.getFile();
            RecentFilesPopup.addToHistory(file);
            BasicNewResourceWizard.selectAndReveal(file, 
                    workbench.getActiveWorkbenchWindow());
            gotoLocation(file.getFullPath(), 0);
            return true;
        }
        else {
            return false;
        }
    }

    private String getDeclarationText() {
        if (page.isDeclaration()) {
            char initial = page.getUnitName().charAt(0);
            String body = pastedText==null ? "{}" : 
                "{" + System.lineSeparator() + 
                    pastedText + 
                    System.lineSeparator() + "}";
            String imports = importText==null || importText.isEmpty() ? "" :
                    importText + System.lineSeparator() + System.lineSeparator();
            if (Character.isUpperCase(initial)) {
                return imports + "class " + page.getUnitName() + "() " + body;
            }
            else {
                return imports + "void " + page.getUnitName() + "() " + body;
            }
        }
        else {
            return pastedText==null ? "" : pastedText;
        }
    }
    
    @Override
    public void addPages() {
        super.addPages();
        if (page==null) {
            page = new NewUnitWithDeclarationWizardPage();
            page.init(workbench, selection);
            if (unitName!=null) {
                page.setUnitName(unitName);
            }
            if (title!=null) {
                page.setTitle(title);
            }
            if (description!=null) {
                page.setDescription(description);
            }
        }
        addPage(page);
    }

}
