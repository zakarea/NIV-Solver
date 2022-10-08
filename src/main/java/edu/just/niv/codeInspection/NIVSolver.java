// Copyright 2000-2022 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package edu.just.niv.codeInspection;

import com.intellij.analysis.problemsView.toolWindow.Root;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.StringTokenizer;

import static com.siyeh.ig.psiutils.ExpressionUtils.isNullLiteral;

/**
 * Implements an inspection to detect next-intent vulnerability (NIV).
 * The quick fix converts these comparisons by using PendingIntent.
 */
public class NIVSolver extends AbstractBaseJavaLocalInspectionTool {

  // Defines the text of the quick fix intention
  public static final String QUICK_FIX_NAME = "NIV Solver: use PendingIntent ";
  public static int requestCode = 1;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ComparingReferencesInspection");
  private final CriQuickFix myQuickFix = new CriQuickFix();
  // This string holds a list of classes relevant to this inspection.
  @SuppressWarnings({"WeakerAccess"})
  @NonNls
  public String CHECKED_CLASSES = "java.lang.String;java.util.Date";

  public ArrayList<PsiMethodCallExpression> nestedIntents = new ArrayList<PsiMethodCallExpression>();
  /**
   * This method is called to get the panel describing the inspection.
   * It is called every time the user selects the inspection in preferences.
   * The user has the option to edit the list of {@link #CHECKED_CLASSES}.
   *
   * @return panel to display inspection information.
   */
  @Override
  public JComponent createOptionsPanel() {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    final JTextField checkedClasses = new JTextField(CHECKED_CLASSES);
    checkedClasses.getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(@NotNull DocumentEvent event) {
        CHECKED_CLASSES = checkedClasses.getText();
      }
    });
    panel.add(checkedClasses);
    return panel;
  }

  /**
   * This method is overridden to provide a custom visitor.
   * that inspects expressions with relational operators '==' and '!='.
   * The visitor must not be recursive and must be thread-safe.
   *
   * @param holder     object for visitor to register problems found.
   * @param isOnTheFly true if inspection was run in non-batch mode
   * @return non-null visitor for this inspection.
   * @see JavaElementVisitor
   */
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      /**
       * This string defines the short message shown to a user signaling the inspection found a problem.
       * It reuses a string from the inspections bundle.
       */
      @NonNls
      private final String DESCRIPTION_TEMPLATE_Android = "NIV Solver " +
              InspectionsBundle.message("inspection.comparing.references.problem.descriptor");

      /**
       * Avoid defining visitors for both Reference and Binary expressions.
       *
       * @param psiReferenceExpression The expression to be evaluated.
       */
      @Override
      public void visitReferenceExpression(PsiReferenceExpression psiReferenceExpression) {
      }

//      @Override
//      public void visitDeclarationStatement(PsiDeclarationStatement statement) {
//        super.visitDeclarationStatement(statement);
//      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);

        String method = expression.getText();
        //check startActivity|startService|bindService with nested Intent object
        if(method.contains("startActivity") || method.contains("startService") || method.contains("bindService")){
//          System.out.println("method = " + method);
          if(expression.getArgumentList() != null){
            PsiExpression[] pex = expression.getArgumentList().getExpressions();
            for(PsiExpression ex : pex){
              if(ex.getReferences().length == 0){
                continue;
              }
              PsiElement e = ex.getReferences()[0].resolve();
              if(! (e instanceof PsiLocalVariable)){
                continue;
              }
              PsiLocalVariable localVariable = (PsiLocalVariable) e;
              System.out.println(e.getText());

              final PsiReference[] refs = ReferencesSearch.search(localVariable, GlobalSearchScope.projectScope(localVariable.getProject()), true).toArray(PsiReference.EMPTY_ARRAY);
              for(PsiReference ref : refs){

                if (ref != null) {
                  PsiElement usageElement = ref.getElement().getParent().getParent();

                  if(usageElement instanceof PsiMethodCallExpression){
                    PsiMethodCallExpression usageMethodCallExpression = (PsiMethodCallExpression) usageElement;
                    String methodCall = usageMethodCallExpression.getText();
                    System.out.println("[Stage 2] " + methodCall);

                    if(usageMethodCallExpression.getArgumentList() != null && methodCall.contains("putExtras")){

                      PsiExpression[] pex2 = usageMethodCallExpression.getArgumentList().getExpressions();

                      for(PsiExpression ex2 : pex2){
                        if(ex2.getReferences().length == 0){
                          continue;
                        }
                        PsiElement e2= ex2.getReferences()[0].resolve();

                        if(! (e2 instanceof PsiLocalVariable)){
                          continue;
                        }

                        PsiLocalVariable localVariable2 = (PsiLocalVariable) e2;
                        System.out.println(e2.getText());
                        //check first senario
                        if(localVariable2.getType().equalsToText("android.content.Intent")){
                          holder.registerProblem(expression, DESCRIPTION_TEMPLATE_Android, myQuickFix);
                        }
                        //check second senario
                        else if(localVariable2.getType().equalsToText("android.os.Bundle")){
                          final PsiReference[] refs2 = ReferencesSearch.search(localVariable2, GlobalSearchScope.projectScope(localVariable2.getProject()), true).toArray(PsiReference.EMPTY_ARRAY);
                          for (PsiReference ref2 : refs2) {

                              if (ref2 != null) {
                                PsiElement usageElement2 = ref2.getElement().getParent().getParent();

                              if (usageElement2 instanceof PsiMethodCallExpression) {
                                PsiMethodCallExpression usageMethodCallExpression2 = (PsiMethodCallExpression) usageElement2;
                                String methodCall2 = usageMethodCallExpression2.getText();
                                System.out.println("[Stage 3] " + methodCall2);
                                check(expression, usageMethodCallExpression2);
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }

        System.out.println("------------------------------------------------");

//        if(method.contains("putExtras")){
//          System.out.println(method);
//
//          PsiReferenceExpression mExp = expression.getMethodExpression();
//          PsiType callerType = mExp.getType();
//          System.out.println("caller type = " + callerType.getCanonicalText());
//
//          //check patterns [Intent].putExtras([Intent]);
//          if(callerType.getCanonicalText().equals("android.content.Intent")){
//            nestedIntents.add(expression);
//            holder.registerProblem(expression,
//                    DESCRIPTION_TEMPLATE_Android, myQuickFix);
//          }
//
//          if(mExp.getQualifierExpression() != null){
//            System.out.println("1 " + mExp.getQualifierExpression().getText());
//          }
//
//          if(mExp.getQualifiedName() != null){
//            System.out.println("2 " + mExp.getQualifiedName());
//          }
//
//          if(mExp.getReferenceName() != null){
//            System.out.println("3 " + mExp.getReferenceName());
//          }
//
//
//          if(expression.getArgumentList() != null){
//            System.out.println("Parameters = " + expression.getArgumentList().getText());
////            System.out.println("parameters type = " + expression.getArgumentList().findElementAt(0).getReference().getCanonicalText());
//              System.out.println("parameters type = " + expression.getArgumentList().getExpressions()[0].getType().getCanonicalText());
//          }
//
//          System.out.println("---------------------------------------");
//
////          PsiReference identifier = mExp.getReference();
////          if(identifier != null)
////            System.out.println("reference = " + identifier.getCanonicalText());
//
////          PsiReferenceParameterList paramsList = expression.getTypeArgumentList();
////          PsiTypeElement paramsType[] = paramsList.getTypeParameterElements();
////          System.out.println("Param types");
////          for(PsiTypeElement t : paramsType){
////            System.out.print(t.getType().getCanonicalText() + "   ");
////          }
//
////          PsiReferenceParameterList params = mExp.getParameterList();
////          PsiTypeElement paramsType[] = params.getTypeParameterElements();
////          System.out.println("Param types");
////          for(PsiTypeElement t : paramsType){
////            System.out.println(t.getType().getCanonicalText());
////          }
//
//        }

      }

      private void check(PsiMethodCallExpression expression, PsiMethodCallExpression usageMethodCallExpression){
        PsiExpression[] pex2 = usageMethodCallExpression.getArgumentList().getExpressions();

        for(PsiExpression ex2 : pex2){
          if(ex2.getReferences().length == 0){
            continue;
          }
          PsiElement e2= ex2.getReferences()[0].resolve();

          if(! (e2 instanceof PsiLocalVariable)){
            continue;
          }

          PsiLocalVariable localVariable2 = (PsiLocalVariable) e2;
          System.out.println(e2.getText());
          //check like first senario
          if(localVariable2.getType().equalsToText("android.content.Intent")){
            holder.registerProblem(expression, DESCRIPTION_TEMPLATE_Android, myQuickFix);
          }
        }
      }

    };
  }

  /**
   * This class provides a solution to inspection problem expressions by manipulating the PSI tree to use 'a.equals(b)'
   * instead of '==' or '!='.
   */
  private static class CriQuickFix implements LocalQuickFix {

    /**
     * Returns a partially localized string for the quick fix intention.
     * Used by the test code for this plugin.
     *
     * @return Quick fix short name.
     */
    @NotNull
    @Override
    public String getName() {
      return QUICK_FIX_NAME;
    }

    /**
     * This method manipulates the PSI tree to replace 'a==b' with 'a.equals(b)' or 'a!=b' with '!a.equals(b)'.
     *
     * @param project    The project that contains the file being edited.
     * @param descriptor A problem found by this inspection.
     */
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {



      try {

        PsiMethodCallExpression expression = (PsiMethodCallExpression) descriptor.getPsiElement();

        PsiExpression intentRef = expression.getArgumentList().getExpressions()[0];
        String methodName = "getActivity";
        if(expression.getText().contains("startActivity")){
          methodName = "getActivity";
        }else if(expression.getText().contains("startService") || expression.getText().contains("bindService")){
          methodName = "getService";
        }
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

        //add import statment
        final PsiClass clazz =  PsiTreeUtil.getParentOfType(intentRef, PsiClass.class);
        final PsiFile psiFile = expression.getContainingFile();
        final PsiJavaFile javaFile = (PsiJavaFile) psiFile;
        final PsiImportList importList = javaFile.getImportList();
        if(importList.findOnDemandImportStatement("android.app") == null)
          importList.add(factory.createImportStatementOnDemand("android.app"));


        //change method call
        PsiMethodCallExpression newExpression =
                (PsiMethodCallExpression) factory.createExpressionFromText(
                        "PendingIntent." +
                                methodName +
                                "(getApplicationContext(), /* CHANGE REQUEST CODE HERE */" + (requestCode++) + ", " +
                                intentRef.getText() +
                                ", /* flags */ PendingIntent.FLAG_IMMUTABLE)",
                        null);
        PsiExpression result = (PsiExpression) expression.replace(newExpression);


        //update AndroidManifest.xml
        System.out.println(clazz.getName());
        String activityName = clazz.getName();

        PsiFile[] files = FilenameIndex.getFilesByName(project, "AndroidManifest.xml",  GlobalSearchScope.projectScope(project));
        System.out.println("found >> " + files[0].getName());
        XmlFile manifestFile = (XmlFile) files[0];

        DomManager manager = DomManager.getDomManager(project);
        DomElement document = manager.getFileElement(manifestFile).getRootElement();
        XmlTag rootTag = document.getXmlTag(); // manifest
        System.out.println(rootTag);
        if (rootTag != null) {
          XmlTag app = rootTag.getSubTags()[0];
          System.out.println(app);
          XmlTag[] activities = app.getSubTags();
          for(XmlTag activity : activities){
            System.out.println(activity.getName());
            System.out.println(activity.getAttribute("android:name").getValue());
            if(activity.getAttribute("android:name").getValue().equals("."+activityName)){
              System.out.println("EQUAL1");
              if(activity.getAttribute("android:exported").getValue().equals("true")){
                System.out.println("EQUAL2");
                activity.getAttribute("android:exported").setValue("false");
                System.out.println("android:exported = false" );
              }

            }
          }
        }


      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

  }

}
