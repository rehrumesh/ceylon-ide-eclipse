package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.eclipse.code.complete.CeylonCompletionProcessor.getProposals;
import static com.redhat.ceylon.eclipse.code.correct.CorrectionUtil.getLevenshteinDistance;
import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.importEdits;
import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.isImported;
import static com.redhat.ceylon.eclipse.ui.CeylonResources.MINOR_CHANGE;
import static com.redhat.ceylon.eclipse.util.Nodes.findNode;
import static com.redhat.ceylon.eclipse.util.Nodes.getIdentifyingNode;
import static com.redhat.ceylon.eclipse.util.Nodes.getOccurrenceLocation;
import static com.redhat.ceylon.eclipse.util.OccurrenceLocation.IMPORT;
import static java.lang.Character.isUpperCase;
import static java.util.Collections.singleton;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.DeclarationWithProximity;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.NamedArgumentList;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ParameterList;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.util.EditorUtil;
import com.redhat.ceylon.eclipse.util.Highlights;
import com.redhat.ceylon.eclipse.util.OccurrenceLocation;

class ChangeReferenceProposal extends CorrectionProposal 
        implements ICompletionProposalExtension {
    
    private ChangeReferenceProposal(ProblemLocation problem, 
            String name, String pkg, TextFileChange change) {
        super("Change reference to '" + name + "'" + pkg, change, 
                new Region(problem.getOffset(), name.length()), 
                MINOR_CHANGE);
    }
    
    static void addChangeReferenceProposal(ProblemLocation problem,
            Collection<ICompletionProposal> proposals, IFile file,
            String brokenName, Declaration dec, int dist,
            Tree.CompilationUnit cu) {
        TextFileChange change = 
                new TextFileChange("Change Reference", file);
        change.setEdit(new MultiTextEdit());
        IDocument doc = EditorUtil.getDocument(change);
        String pkg = "";
        if (dec.isToplevel() && 
                !isImported(dec, cu) && 
                isInPackage(cu, dec)) {
            String pn = dec.getContainer().getQualifiedNameString();
            pkg = " in '" + pn + "'";
            if (!pn.isEmpty() && 
                    !pn.equals(Module.LANGUAGE_MODULE_NAME)) {
                OccurrenceLocation ol = 
                        getOccurrenceLocation(cu, 
                                findNode(cu, problem.getOffset()),
                                problem.getOffset());
                if (ol!=IMPORT) {
                    List<InsertEdit> ies = 
                            importEdits(cu, singleton(dec), 
                                    null, null, doc);
                    for (InsertEdit ie: ies) {
                        change.addEdit(ie);
                    }
                }
            }
        }
        change.addEdit(new ReplaceEdit(problem.getOffset(), 
                brokenName.length(), dec.getName())); //Note: don't use problem.getLength() because it's wrong from the problem list
        proposals.add(new ChangeReferenceProposal(problem, 
                dec.getName(), pkg, change));
    }

    protected static boolean isInPackage(Tree.CompilationUnit cu,
            Declaration dec) {
        return !dec.getUnit().getPackage()
                .equals(cu.getUnit().getPackage());
    }

    @Override
    public void apply(IDocument document, char trigger, int offset) {
        apply(document);
    }

    @Override
    public boolean isValidFor(IDocument document, int offset) {
        return true;
    }

    @Override
    public char[] getTriggerCharacters() {
        return "r".toCharArray();
    }

    @Override
    public int getContextInformationPosition() {
        return -1;
    }

    static void addChangeReferenceProposals(Tree.CompilationUnit cu, 
            Node node, ProblemLocation problem, 
            Collection<ICompletionProposal> proposals, IFile file) {
        Node id = getIdentifyingNode(node);
        if (id!=null) {
            String brokenName = id.getText();
            if (brokenName!=null && !brokenName.isEmpty()) {
                Collection<DeclarationWithProximity> dwps = 
                        getProposals(node, node.getScope(), cu).values();
                for (DeclarationWithProximity dwp: dwps) {
                    processProposal(cu, problem, proposals, file,
                            brokenName, dwp.getDeclaration());
                }
            }
        }
    }

    static void addChangeArgumentReferenceProposals(Tree.CompilationUnit cu, 
            Node node, ProblemLocation problem, 
            Collection<ICompletionProposal> proposals, IFile file) {
        String brokenName = getIdentifyingNode(node).getText();
        if (brokenName!=null && !brokenName.isEmpty()) {
            if (node instanceof Tree.NamedArgument) {
                Scope scope = node.getScope();
                if (!(scope instanceof NamedArgumentList)) {
                    scope = scope.getScope(); //for declaration-style named args
                }
                NamedArgumentList namedArgumentList = 
                        (NamedArgumentList) scope;
                ParameterList parameterList = 
                        namedArgumentList.getParameterList();
                if (parameterList!=null) {
                    for (Parameter parameter: parameterList.getParameters()) {
                        Declaration declaration = parameter.getModel();
                        if (declaration!=null) {
                            processProposal(cu, problem, proposals, file,
                                    brokenName, declaration);
                        }
                    }
                }
            }
        }
    }

    private static void processProposal(Tree.CompilationUnit cu,
            ProblemLocation problem, Collection<ICompletionProposal> proposals,
            IFile file, String brokenName, Declaration declaration) {
        String name = declaration.getName();
        if (!brokenName.equals(name) &&
                isUpperCase(name.charAt(0))==isUpperCase(brokenName.charAt(0))) {
            int dist = getLevenshteinDistance(brokenName, name); //+dwp.getProximity()/3;
            //TODO: would it be better to just sort by dist, and
            //      then select the 3 closest possibilities?
            if (dist<=brokenName.length()/3+1) {
                addChangeReferenceProposal(problem, proposals, file, 
                        brokenName, declaration, dist, cu);
            }
        }
    }
    @Override
    public StyledString getStyledDisplayString() {
        return Highlights.styleProposal(getDisplayString(), true);
    }
    
}