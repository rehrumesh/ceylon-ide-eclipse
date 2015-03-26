package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.eclipse.util.Indents.getDefaultIndent;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultLineDelimiter;
import static com.redhat.ceylon.eclipse.util.Indents.getIndent;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.ui.texteditor.DeleteLineAction;

import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Statement;

public class JoinIfStatementProposal {
	static void addJoinIfStatementProposal(
            Collection<ICompletionProposal> proposals, IDocument doc,
            IFile file, Tree.Statement statement){
		
		if (statement instanceof Tree.IfStatement) {
			Tree.IfStatement is = (Tree.IfStatement) statement;
			Tree.IfClause topIfClause =  is.getIfClause();
			Tree.Block block =  topIfClause.getBlock();
			List<Statement> statementList = block.getStatements();
			
			if(statementList.size() == 1 && statementList.get(0) instanceof Tree.IfStatement){
				Tree.IfStatement innerIs = (Tree.IfStatement) statementList.get(0);
				if(innerIs != null && innerIs.getElseClause() == null){
					TextChange change = new TextFileChange("Join if Statements", file);
                    change.setEdit(new MultiTextEdit());
                    String ws;
                    String indent; 
                    ws = getDefaultLineDelimiter(doc) + getIndent(is, doc);
                    indent = getDefaultIndent();  
                    
//                    change.addEdit(new ReplaceEdit(c1.getStopIndex()+1, 
//                            c2.getStartIndex()-c1.getStopIndex()-1, 
//                            ") {" + ws + indent + "if ("));
//                    change.addEdit(new InsertEdit(is.getStopIndex()+1, ws + "}"));
//                    if (!indent.isEmpty()) {
//                        try {
//                            for (int line = doc.getLineOfOffset(cl.getStopIndex())+1;
//                                    line <= doc.getLineOfOffset(is.getStopIndex()); 
//                                    line++) {
//                                change.addEdit(new InsertEdit(doc.getLineOffset(line), indent));
//                            }
//                        }
//                        catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                    }
                    
                    /*Tree.ConditionList cl = is.getIfClause().getConditionList();
                    if (cl!=null) {
                        List<Tree.Condition> conditions = cl.getConditions();
                        if(conditions.size()>0){
                        	Tree.Condition rootIfFinalCondition = conditions.get(conditions.size()-1);                        	
                        	Tree.Condition firstInnerIfcondition =innerIs.getIfClause().getConditionList().getConditions().get(0);
                        	//int lineLength=0;
	                        //try{
	                        //	lineLength = doc.getLineLength(doc.getLineOfOffset(is.getStopIndex()));
	                        //}
	                        //catch (Exception e) {
	                        //    e.printStackTrace();
	                        //}
                        	//int length = lineLength -rootIfFinalCondition.getStopIndex()-1 +firstInnerIfcondition.getStartIndex();
                        	//innerIs.getIfClause().getExpression().
                        	
                        	change.addEdit(new ReplaceEdit(rootIfFinalCondition.getStopIndex()+1, 0,", "+innerIs.getIfClause().getExpression().getText()+""));
                        }                        
                    }*/
                    System.out.println("Bummmmmmmmmmmmmmmmm");
                    proposals.add(new CorrectionProposal("Join if statements", change, null));
				}
			}
			
		}
	}
}
