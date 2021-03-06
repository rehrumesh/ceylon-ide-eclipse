package com.redhat.ceylon.eclipse.code.refactor;

import static com.redhat.ceylon.compiler.typechecker.model.Util.addToUnion;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.LINE_COMMENT;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.MULTI_COMMENT;
import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.applyImports;
import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.importType;
import static com.redhat.ceylon.eclipse.util.EditorUtil.getSelection;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultIndent;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultLineDelimiter;
import static com.redhat.ceylon.eclipse.util.Indents.getIndent;
import static java.util.Collections.singletonList;
import static org.antlr.runtime.Token.HIDDEN_CHANNEL;
import static org.eclipse.ltk.core.refactoring.RefactoringStatus.createWarningStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.antlr.runtime.CommonToken;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Region;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.ui.IEditorPart;

import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.UnionType;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.model.Util;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.util.FindContainerVisitor;
import com.redhat.ceylon.eclipse.util.Indents;
import com.redhat.ceylon.eclipse.util.Nodes;

public class ExtractFunctionRefactoring extends AbstractRefactoring {
    
    private final class FindOuterReferencesVisitor extends Visitor {
        final Declaration declaration;
        int refs = 0;
        FindOuterReferencesVisitor(Declaration declaration) {
            this.declaration = declaration;
        }
        @Override
        public void visit(Tree.MemberOrTypeExpression that) {
            super.visit(that);
            Declaration dec = that.getDeclaration();
            if (declaration.equals(dec)) {
                refs++;
            }
        }
        @Override
        public void visit(Tree.Declaration that) {
            super.visit(that);
            Declaration dec = that.getDeclarationModel();
            if (declaration.equals(dec)) {
                refs++;
            }
        }
        @Override
        public void visit(Tree.Type that) {
            super.visit(that);
            ProducedType type = that.getTypeModel();
            if (type!=null) {
                TypeDeclaration dec = type.getDeclaration();
                if (dec instanceof ClassOrInterface &&
                        declaration.equals(dec)) {
                    refs++;
                }
            }
        }
    }
    
    private final class CheckExpressionVisitor extends Visitor {
        String problem = null;
        @Override
        public void visit(Tree.Body that) {}
        @Override
        public void visit(Tree.AssignmentOp that) {
            super.visit(that);
            problem = "an assignment";
        }
    }

    private final class CheckStatementsVisitor extends Visitor {
        final Tree.Body scope;
        final Collection<Tree.Statement> statements;
        CheckStatementsVisitor(Tree.Body scope, 
                Collection<Tree.Statement> statements) {
            this.scope = scope;
            this.statements = statements;
        }
        String problem = null;
        @Override
        public void visit(Tree.Body that) {
            if (that.equals(scope)) {
                super.visit(that);
            }
        }
        @Override
        public void visit(Tree.Declaration that) {
            super.visit(that);
            if (result==null || !that.equals(result)) {
                Declaration d = that.getDeclarationModel();
                if (d.isShared()) {
                    problem = "a shared declaration";
                }
                else {
                    if (hasOuterRefs(d, scope, statements)) {
                        problem = "a declaration used elsewhere";
                    }
                }
            }
        }
        @Override
        public void visit(Tree.SpecifierStatement that) {
            super.visit(that);
            if (result==null || !that.equals(result)) {
                if (that.getBaseMemberExpression() instanceof Tree.MemberOrTypeExpression) {
                    Declaration d = 
                            ((Tree.MemberOrTypeExpression) that.getBaseMemberExpression()).getDeclaration();
                    if (notResultRef(d) && hasOuterRefs(d, scope, statements)) {
                        problem = "a specification statement for a declaration used or defined elsewhere";
                    }
                }
            }
        }
        @Override
        public void visit(Tree.AssignmentOp that) {
            super.visit(that);
            if (result==null || !that.equals(result)) {
                if (that.getLeftTerm() instanceof Tree.MemberOrTypeExpression) {
                    Declaration d = 
                            ((Tree.MemberOrTypeExpression) that.getLeftTerm()).getDeclaration();
                    if (notResultRef(d) && hasOuterRefs(d, scope, statements)) {
                        problem = "an assignment to a declaration used or defined elsewhere";
                    }
                }
            }
        }
        private boolean notResultRef(Declaration d) {
            return resultDeclaration==null || !resultDeclaration.equals(d);
        }
        @Override
        public void visit(Tree.Directive that) {
            super.visit(that);
            problem = "a directive statement";
        }
    }

    private boolean hasOuterRefs(Declaration d, Tree.Body scope, 
            Collection<Tree.Statement> statements) {
        if (scope==null) return false; //TODO: what case is this?
        FindOuterReferencesVisitor v = 
                new FindOuterReferencesVisitor(d);
        for (Tree.Statement s: scope.getStatements()) {
            if (!statements.contains(s)) {
                s.visit(v);
            }
        }
        return v.refs>0;
    }
    
    private final class FindResultVisitor extends Visitor {
        Node result = null;
        TypedDeclaration resultDeclaration = null;
        final Tree.Body scope;
        final Collection<Tree.Statement> statements;
        FindResultVisitor(Tree.Body scope, 
                Collection<Tree.Statement> statements) {
            this.scope = scope;
            this.statements = statements;
        }
        @Override
        public void visit(Tree.Body that) {
            if (that instanceof Tree.Block) {
                super.visit(that);
            }
        }
        @Override
        public void visit(Tree.AttributeDeclaration that) {
            super.visit(that);
            Value dec = that.getDeclarationModel();
            if (hasOuterRefs(dec, scope, statements)) {
                result = that;
                resultDeclaration = dec;
            }
        }
        @Override
        public void visit(Tree.AssignmentOp that) {
            super.visit(that);
            Tree.Term leftTerm = that.getLeftTerm();
            if (leftTerm instanceof Tree.StaticMemberOrTypeExpression) {
                Declaration dec = 
                        ((Tree.StaticMemberOrTypeExpression) leftTerm).getDeclaration();
                if (hasOuterRefs(dec, scope, statements) && 
                        isDefinedLocally(dec)) {
                    result = that;
                    resultDeclaration = (TypedDeclaration) dec;
                }
            }
        }
        @Override
        public void visit(Tree.SpecifierStatement that) {
            super.visit(that);
            Tree.Term term = that.getBaseMemberExpression();
            if (term instanceof Tree.StaticMemberOrTypeExpression) {
                Declaration dec = 
                        ((Tree.StaticMemberOrTypeExpression) term).getDeclaration();
                if (hasOuterRefs(dec, scope, statements) && 
                        isDefinedLocally(dec)) {
                    result = that;
                    resultDeclaration = (TypedDeclaration) dec;
                }
            }
        }
        private boolean isDefinedLocally(Declaration dec) {
            return !Util.contains(dec.getScope(), 
                    scope.getScope().getContainer());
        }
    }

    private final class FindReturnsVisitor extends Visitor {
        final Collection<Tree.Return> returns;
        FindReturnsVisitor(Collection<Tree.Return> returns) {
            this.returns = returns;
        }
        @Override
        public void visit(Tree.Declaration that) {}
        @Override
        public void visit(Tree.Return that) {
            super.visit(that);
            if (that.getExpression()!=null) {
                returns.add(that);
            }
        }
    }

    private static final class FindLocalReferencesVisitor extends Visitor {
        List<Tree.BaseMemberExpression> localReferences = 
                new ArrayList<Tree.BaseMemberExpression>();
        private Scope scope;
        private Scope targetScope;
        private FindLocalReferencesVisitor(Scope scope, Scope targetScope) {
            this.scope = scope;
            this.targetScope = targetScope;
        }
        public List<Tree.BaseMemberExpression> getLocalReferences() {
            return localReferences;
        }
        @Override
        public void visit(Tree.BaseMemberExpression that) {
            super.visit(that);
            //TODO: don't treat assignments as references, but
            //      then we have to declare a new local in the
            //      extracted function!
//            if (!that.getAssigned()) {
                //TODO: things nested inside control structures
                Declaration currentDec = that.getDeclaration();
                for (Tree.BaseMemberExpression bme: localReferences) {
                    Declaration dec = bme.getDeclaration();
                    if (dec.equals(currentDec)) {
                        return;
                    }
                    if (currentDec instanceof TypedDeclaration) {
                        TypedDeclaration od = 
                                ((TypedDeclaration)currentDec).getOriginalDeclaration();
                        if (od!=null && od.equals(dec)) return;
                    }
                }
                if (currentDec.isDefinedInScope(scope) && 
                        !currentDec.isDefinedInScope(targetScope)) {
                    localReferences.add(that);
                }
            }
//        }
    }

    private String newName;
    private boolean explicitType;
    private Node result;
    private TypedDeclaration resultDeclaration;
    private List<Tree.Statement> statements;
    List<Tree.Return> returns;
    private ProducedType returnType;

    public ExtractFunctionRefactoring(IEditorPart editor) {
        super(editor);
        if (editor instanceof CeylonEditor) {
            CeylonEditor ce = (CeylonEditor) editor;
            if (ce.getSelectionProvider()!=null) {
                init(getSelection(ce));
            }
        }
        if (resultDeclaration!=null) {
            newName = resultDeclaration.getName();
        }
        else {
            newName = Nodes.nameProposals(node)[0];
            if ("it".equals(newName)) {
                newName = "do";
            }
        }
    }

    private void init(ITextSelection selection) {
        Tree.Body body;
        if (node instanceof Tree.Body) {
            body = (Tree.Body) node;
            statements = getStatements(body, selection);
        }
        else if (node instanceof Tree.Statement) {
            class FindBodyVisitor extends Visitor {
                Tree.Body body;
                @Override
                public void visit(Tree.Body that) {
                    super.visit(that);
                    if (that.getStatements().contains(node)) {
                        body = that;
                    }
                }
            }
            FindBodyVisitor fbv = new FindBodyVisitor();
            fbv.visit(rootNode);
            body = fbv.body;
            statements = singletonList((Tree.Statement) node);
            node = body; //TODO: wow, ugly!!!!!
        }
        else {
            return;
        }
        for (Tree.Statement s: statements) {
            FindResultVisitor v = 
                    new FindResultVisitor(body, statements);
            s.visit(v);
            if (v.result!=null) {
                result = v.result;
                resultDeclaration = v.resultDeclaration;
                break;
            }
        }
        returns = new ArrayList<Tree.Return>();
        for (Tree.Statement s: statements) {
            FindReturnsVisitor v = 
                    new FindReturnsVisitor(returns);
            s.visit(v);
        }
    }

    @Override
    public boolean isEnabled() {
        return sourceFile!=null &&
                isEditable() &&
                !sourceFile.getName().equals("module.ceylon") &&
                !sourceFile.getName().equals("package.ceylon") &&
                (node instanceof Tree.Term || 
                 node instanceof Tree.Body &&
                    !statements.isEmpty() &&
                    !containsConstructor(statements));
    }
    
    private boolean containsConstructor(List<Tree.Statement> statements) {
        for (Tree.Statement statement : statements) {
            if (statement instanceof Tree.Constructor) {
                return true;
            }
        }
        return false;
    }
    
    public String getName() {
        return "Extract Function";
    }
    
    public boolean forceWizardMode() {
        if (node instanceof Tree.Body) {
            Tree.Body body = (Tree.Body) node;
            for (Tree.Statement s: statements) {
                CheckStatementsVisitor v = 
                        new CheckStatementsVisitor(body, statements);
                s.visit(v);
                if (v.problem!=null) {
                    return true;
                }
            }
        }
        else if (node instanceof Tree.Term) {
            CheckExpressionVisitor v = 
                    new CheckExpressionVisitor();
            node.visit(v);
            if (v.problem!=null) {
                return true;
            }
        }
        Declaration existing = node.getScope()
                .getMemberOrParameter(node.getUnit(), newName, null, false);
        return existing!=null;
    }

    public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
            throws CoreException, OperationCanceledException {
        if (node instanceof Tree.Body) {
            Tree.Body body = (Tree.Body) node;
            for (Tree.Statement s: statements) {
                CheckStatementsVisitor v = 
                        new CheckStatementsVisitor(body, statements);
                s.visit(v);
                if (v.problem!=null) {
                    return createWarningStatus("Selected statements contain "
                            + v.problem + " at  " + s.getLocation());
                }
            }
        }
        else if (node instanceof Tree.Term) {
            CheckExpressionVisitor v = 
                    new CheckExpressionVisitor();
            node.visit(v);
            if (v.problem!=null) {
                return createWarningStatus("Selected expression contains "
                        + v.problem);
            }
        }
        return new RefactoringStatus();
    }

    public RefactoringStatus checkFinalConditions(IProgressMonitor pm)
            throws CoreException, OperationCanceledException {
        Declaration existing = node.getScope()
                .getMemberOrParameter(node.getUnit(), newName, null, false);
        if (null!=existing) {
            return createWarningStatus("An existing declaration named '" +
                    newName + "' already exists in the same scope");
        }
        return new RefactoringStatus();
    }

    IRegion decRegion;
    IRegion refRegion;
    IRegion typeRegion;
	private boolean canBeInferred;

    public Change createChange(IProgressMonitor pm) 
            throws CoreException,
                   OperationCanceledException {
        TextChange tfc = newLocalChange();
        extractInFile(tfc);
        return tfc;
    }

    void extractInFile(TextChange tfc) 
            throws CoreException {
        if (node instanceof Tree.Term) {
            extractExpressionInFile(tfc);
        }
        else if (node instanceof Tree.Body) {
            extractStatementsInFile(tfc);
        }
    }

    private void extractExpressionInFile(TextChange tfc) 
            throws CoreException {
        tfc.setEdit(new MultiTextEdit());
        IDocument doc = tfc.getCurrentDocument(null);
        
        Tree.Term term = (Tree.Term) node;
        Integer start = term.getStartIndex();
        int length = term.getStopIndex()-start+1;
        Tree.Term unparened = unparenthesize(term);
        String body;
        if (unparened instanceof Tree.FunctionArgument) {
            Tree.FunctionArgument fa = 
                    (Tree.FunctionArgument) unparened;
            returnType = fa.getType().getTypeModel();
            if (fa.getBlock()!=null) {
                body = toString(fa.getBlock());
            }
            else if (fa.getExpression()!=null) {
                body = "=> " + toString(fa.getExpression()) + ";";
            }
            else {
                body = "=>;";
            }
        }
        else {
            returnType = node.getUnit()
                    .denotableType(term.getTypeModel());
            body = "=> " + toString(unparened) + ";";
        }
        
        FindContainerVisitor fsv = new FindContainerVisitor(term);
        rootNode.visit(fsv);
        Tree.Declaration decNode = fsv.getDeclaration();
        Declaration dec = decNode.getDeclarationModel();
        FindLocalReferencesVisitor flrv = 
                new FindLocalReferencesVisitor(node.getScope(), 
                getContainingScope(decNode));
        term.visit(flrv);
        List<Tree.BaseMemberExpression> localRefs = flrv.getLocalReferences();
        List<TypeDeclaration> localTypes = 
                new ArrayList<TypeDeclaration>();
        for (Tree.BaseMemberExpression bme: localRefs) {
            addLocalType(dec, node.getUnit().denotableType(bme.getTypeModel()), 
                    localTypes, new ArrayList<ProducedType>());
        }
        
        StringBuilder params = new StringBuilder();
        StringBuilder args = new StringBuilder();
        if (!localRefs.isEmpty()) {
            boolean first = true;
            for (Tree.BaseMemberExpression bme: localRefs) {
                if (first) {
                    first = false;
                }
                else {
                    params.append(", ");
                    args.append(", ");
                }
                Declaration pdec = bme.getDeclaration();
                if (pdec instanceof TypedDeclaration && 
                        ((TypedDeclaration) pdec).isDynamicallyTyped()) {
                    params.append("dynamic");
                }
                else {
                    params.append(node.getUnit().denotableType(bme.getTypeModel())
                            .getProducedTypeNameInSource(node.getUnit()));
                }
                params.append(" ").append(bme.getIdentifier().getText());
                args.append(bme.getIdentifier().getText());
            }
        }
        
        String indent = 
                getDefaultLineDelimiter(doc) + 
                getIndent(decNode, doc);
        String extraIndent = indent + getDefaultIndent();

        StringBuilder typeParams = new StringBuilder();
        StringBuilder constraints = new StringBuilder();
        if (!localTypes.isEmpty()) {
            typeParams.append("<");
            boolean first = true;
            for (TypeDeclaration t: localTypes) {
                if (first) {
                    first = false;
                }
                else {
                    typeParams.append(", ");
                }
                typeParams.append(t.getName());
                if (!t.getSatisfiedTypes().isEmpty()) {
                    constraints.append(extraIndent).append(getDefaultIndent()) 
                            .append("given ").append(t.getName()).append(" satisfies ");
                    boolean firstConstraint = true;
                    for (ProducedType pt: t.getSatisfiedTypes()) {
                        if (firstConstraint) {
                            firstConstraint = false;
                        }
                        else {
                            constraints.append("&");
                        }
                        constraints.append(pt.getProducedTypeNameInSource(node.getUnit()));
                    }
                }
            }
            typeParams.append(">");
        }
        
        int il;
        String type;
        if (returnType==null || returnType.isUnknown()) {
            type = "dynamic";
            il = 0;
        }
        else {
            TypeDeclaration rtd = returnType.getDeclaration();
            boolean isVoid = 
                    rtd instanceof Class && 
                    rtd.equals(term.getUnit().getAnythingDeclaration());
            if (isVoid) {
                type = "void";
                il = 0;
            }
            else if (explicitType || dec.isToplevel()) {
                type = returnType.getProducedTypeNameInSource(node.getUnit());
                HashSet<Declaration> decs = new HashSet<Declaration>();
                importType(decs, returnType, rootNode);
                il = applyImports(tfc, decs, rootNode, doc);
            }
            else {
                type = "function";
                il = 0;
                canBeInferred = true;
            }
        }

        String text = 
                type + " " + newName + typeParams + "(" + params + ")" + 
                constraints + " " + body + indent + indent;
        String invocation;
        int refStart;
        if (unparened instanceof Tree.FunctionArgument) {
            Tree.FunctionArgument fa = (Tree.FunctionArgument) node;
            Tree.ParameterList cpl = fa.getParameterLists().get(0);
            if (cpl.getParameters().size()==localRefs.size()) {
                invocation = newName;
                refStart = start;
            }
            else {
                String header = Nodes.toString(cpl, tokens) + " => ";
                invocation = header + newName + "(" + args + ")";
                refStart = start + header.length();
            }
        }
        else {
            invocation = newName + "(" + args + ")";
            refStart = start;
        }
        Integer decStart = decNode.getStartIndex();
        tfc.addEdit(new InsertEdit(decStart, text));
        tfc.addEdit(new ReplaceEdit(start, length, invocation));
        typeRegion = new Region(decStart+il, type.length());
        decRegion = new Region(decStart+il+type.length()+1, newName.length());
        refRegion = new Region(refStart+il+text.length(), newName.length());
    }

    private Scope getContainingScope(Tree.Declaration decNode) {
        return decNode.getDeclarationModel().getContainer();
    }

    private void extractStatementsInFile(TextChange tfc) 
            throws CoreException {
        tfc.setEdit(new MultiTextEdit());
        IDocument doc = tfc.getCurrentDocument(null);
        final Unit unit = node.getUnit();
        
        Tree.Body body = (Tree.Body) node;
        
        Integer start = statements.get(0).getStartIndex();
        int length = statements.get(statements.size()-1)
                .getStopIndex()-start+1;
        FindContainerVisitor fsv = new FindContainerVisitor(body);
        rootNode.visit(fsv);
        Tree.Declaration decNode = fsv.getDeclaration();
        final Declaration dec = decNode.getDeclarationModel();
        FindLocalReferencesVisitor flrv = 
                new FindLocalReferencesVisitor(node.getScope(),
                getContainingScope(decNode));
        for (Tree.Statement s: statements) {
            s.visit(flrv);
        }
        final List<TypeDeclaration> localTypes = 
                new ArrayList<TypeDeclaration>();
        for (Tree.BaseMemberExpression bme: flrv.getLocalReferences()) {
            addLocalType(dec, unit.denotableType(bme.getTypeModel()), 
                  localTypes, new ArrayList<ProducedType>());
        }
        for (Tree.Statement s: statements) {
            new Visitor() {
                public void visit(Tree.TypeArgumentList that) {
                    for (ProducedType pt: that.getTypeModels()) {
                        addLocalType(dec, unit.denotableType(pt), 
                                localTypes, new ArrayList<ProducedType>());
                    }
                }
            }.visit(s);
        }
        
        HashSet<Declaration> movingDecs = new HashSet<Declaration>();
        for (Tree.Statement s: statements) {
            if (s instanceof Tree.Declaration) {
                movingDecs.add(((Tree.Declaration) s).getDeclarationModel());
            }
        }
        
        String params = "";
        String args = "";
        Set<Declaration> done = new HashSet<Declaration>(movingDecs);
        boolean nonempty = false;
        for (Tree.BaseMemberExpression bme: flrv.getLocalReferences()) {
            Declaration bmed = bme.getDeclaration();
            if (resultDeclaration==null ||
                    !bmed.equals(resultDeclaration) || 
                    resultDeclaration.isVariable()) { //TODO: wrong condition, check if initialized!
                if (done.add(bmed)) {
                    if (bmed instanceof Value && ((Value) bmed).isVariable()) {
                        params += "variable ";
                    }
                    if (bmed instanceof TypedDeclaration && 
                            ((TypedDeclaration) bmed).isDynamicallyTyped()) {
                        params += "dynamic";
                    }
                    else {
                        params += unit.denotableType(bme.getTypeModel())
                                .getProducedTypeNameInSource(node.getUnit());
                    }
                    params += " " + bme.getIdentifier().getText() + ", ";
                    args += bme.getIdentifier().getText() + ", ";
                    nonempty = true;
                }
            }
        }
        if (nonempty) {
            params = params.substring(0, params.length()-2);
            args = args.substring(0, args.length()-2);
        }
        
        String indent = Indents.getDefaultLineDelimiter(doc) + 
                getIndent(decNode, doc);
        String extraIndent = indent + getDefaultIndent();

        String typeParams = "";
        String constraints = "";
        if (!localTypes.isEmpty()) {
            for (TypeDeclaration t: localTypes) {
                typeParams += t.getName() + ", ";
                if (!t.getSatisfiedTypes().isEmpty()) {
                    constraints += extraIndent + getDefaultIndent() + 
                            "given " + t.getName() + " satisfies ";
                    for (ProducedType pt: t.getSatisfiedTypes()) {
                        constraints += pt.getProducedTypeNameInSource(node.getUnit()) + "&";
                    }
                    constraints = constraints.substring(0, constraints.length()-1);
                }
            }
            typeParams = "<" + typeParams.substring(0, typeParams.length()-2) + ">";
        }
        
        if (resultDeclaration!=null) {
            returnType = unit.denotableType(resultDeclaration.getType());
        }
        else if (!returns.isEmpty())  {
            UnionType ut = new UnionType(unit);
            List<ProducedType> list = new ArrayList<ProducedType>();
            for (Tree.Return r: returns) {
                addToUnion(list, r.getExpression().getTypeModel());
            }
            ut.setCaseTypes(list);
            returnType = ut.getType();
        }
        else {
            returnType = null;
        }
        String content;
        int il = 0;
        if (resultDeclaration!=null || !returns.isEmpty()) {
            if (returnType.isUnknown()) {
                content = "dynamic";
            }
            else if (explicitType||dec.isToplevel()) {
                content = returnType.getProducedTypeNameInSource(node.getUnit());
                HashSet<Declaration> already = new HashSet<Declaration>();
                importType(already, returnType, rootNode);
                il = applyImports(tfc, already, rootNode, doc);
            }
            else {
                content = "function";
            }
        }
        else {
            content = "void";
        }
        content += " " + newName + typeParams + "(" + params + ")" + 
                constraints + " {";
        if (resultDeclaration!=null && 
                !(result instanceof Tree.Declaration) &&
                !resultDeclaration.isVariable()) { //TODO: wrong condition, check if initialized!
            content += extraIndent +
                resultDeclaration.getType().getProducedTypeNameInSource(unit) +
                " " + resultDeclaration.getName() + ";";
        }
        Tree.Statement last = statements.isEmpty() ?
                null : statements.get(statements.size()-1);
        for (Tree.Statement s: statements) {
            content += extraIndent + toString(s);
            int i = s.getEndToken().getTokenIndex();
            CommonToken tok;
            while ((tok=tokens.get(++i)).getChannel()==HIDDEN_CHANNEL) {
                String text = tok.getText();
                if (tok.getType()==LINE_COMMENT) {
                    content += " " + text.substring(0, text.length()-1);
                    if (s==last) {
                        length += text.length();
                    }
                }
                if (tok.getType()==MULTI_COMMENT) {
                    content += " " + text;
                    if (s==last) {
                        length += text.length()+1;
                    }
                }
            }
        }
        if (resultDeclaration!=null) {
            content += extraIndent + "return " + 
                    resultDeclaration.getName() + ";";
        }
        content += indent + "}" + indent + indent;
        
        String invocation = newName + "(" + args + ");";
        if (resultDeclaration!=null) {
            String modifs = "";
            if (result instanceof Tree.AttributeDeclaration) {
                if (resultDeclaration.isShared()) {
                    modifs = "shared " + 
                            returnType.getProducedTypeNameInSource(node.getUnit()) + 
                            " ";
                }
                else {
                    modifs = "value ";
                }
            }
            invocation = modifs + resultDeclaration.getName() + 
                    "=" + invocation;
        }
        else if (!returns.isEmpty()) {
            invocation = "return " + invocation;
        }
        
        Integer decStart = decNode.getStartIndex();
        tfc.addEdit(new InsertEdit(decStart, content));        
        tfc.addEdit(new ReplaceEdit(start, length, invocation));
        typeRegion = new Region(decStart+il, content.indexOf(' '));
        decRegion = new Region(decStart+il+content.indexOf(' ')+1, newName.length());
        refRegion = new Region(start+content.length()+il+invocation.indexOf('=')+1, newName.length());
    }

    private List<Tree.Statement> getStatements(Tree.Body body, ITextSelection selection) {
        List<Tree.Statement> statements = new ArrayList<Tree.Statement>();
        for (Tree.Statement s: body.getStatements()) {
            if (s.getStartIndex()>=selection.getOffset() &&
                    s.getStopIndex()<=selection.getOffset()+selection.getLength()) {
                statements.add(s);
            }
        }
        return statements;
    }

    private void addLocalType(Declaration dec, ProducedType type,
            List<TypeDeclaration> localTypes, List<ProducedType> visited) {
        if (visited.contains(type)) {
            return;
        }
        else {
            visited.add(type);
        }
        TypeDeclaration td = type.getDeclaration();
        if (td.getContainer()==dec) {
            boolean found=false;
            for (TypeDeclaration typeDeclaration: localTypes) {
                if (typeDeclaration==td) {
                    found=true; 
                    break;
                }
            }
            if (!found) {
                localTypes.add(td);
            }
        }
        for (ProducedType pt: td.getSatisfiedTypes()) {
            addLocalType(dec, pt, localTypes, visited);
        }
        for (ProducedType pt: type.getTypeArgumentList()) {
            addLocalType(dec, pt, localTypes, visited);
        }
    }


    public void setNewName(String text) {
        newName = text;
    }
    
    public String getNewName() {
        return newName;
    }
    
    public void setExplicitType() {
        this.explicitType = !explicitType;
    }

    ProducedType getType() {
        return returnType;
    }
    
	public String[] getNameProposals() {
		return Nodes.nameProposals(node);
	}
    
    public boolean canBeInferred() {
        return canBeInferred;
    }

}
