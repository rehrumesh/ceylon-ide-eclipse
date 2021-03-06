/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package com.redhat.ceylon.eclipse.core.model.mirror;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.env.IDependent;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.LocalTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.SourceTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeVariableBinding;

import com.redhat.ceylon.compiler.loader.AbstractModelLoader;
import com.redhat.ceylon.compiler.loader.ModelResolutionException;
import com.redhat.ceylon.compiler.loader.mirror.AnnotationMirror;
import com.redhat.ceylon.compiler.loader.mirror.ClassMirror;
import com.redhat.ceylon.compiler.loader.mirror.FieldMirror;
import com.redhat.ceylon.compiler.loader.mirror.MethodMirror;
import com.redhat.ceylon.compiler.loader.mirror.PackageMirror;
import com.redhat.ceylon.compiler.loader.mirror.TypeMirror;
import com.redhat.ceylon.compiler.loader.mirror.TypeParameterMirror;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.eclipse.core.model.JDTModelLoader;
import com.redhat.ceylon.eclipse.core.model.JDTModelLoader.ActionOnClassBinding;
import com.redhat.ceylon.eclipse.core.model.JDTModelLoader.ActionOnResolvedType;

class UnknownClassMirror implements ClassMirror {
    static final String unknown = "unknown";
    
    @Override
    public String getName() {
        return unknown;
    }
    @Override
    public AnnotationMirror getAnnotation(String type) {
        return null;
    }
    @Override
    public boolean isPublic() {
        return true;
    }
    @Override
    public boolean isProtected() {
        return false;
    }
    @Override
    public boolean isDefaultAccess() {
        return false;
    }
    @Override
    public boolean isInterface() {
        return false;
    }
    @Override
    public boolean isAnnotationType() {
        return false;
    }
    @Override
    public boolean isAbstract() {
        return false;
    }
    @Override
    public boolean isStatic() {
        return false;
    }
    @Override
    public boolean isInnerClass() {
        return false;
    }
    @Override
    public boolean isLocalClass() {
        return false;
    }
    @Override
    public boolean isAnonymous() {
        return false;
    }
    @Override
    public boolean isEnum() {
        return false;
    }
    @Override
    public String getQualifiedName() {
        return unknown;
    }
    @Override
    public String getFlatName() {
        return unknown;
    }
    
    static class DefaultPackage implements PackageMirror {
        private final String name = "";

        @Override
        public String getQualifiedName() {
            return name;
        }
        
    }
    static PackageMirror defaultPackage = new DefaultPackage();
    @Override
    public PackageMirror getPackage() {
        return defaultPackage;
    }

    @Override
    public List<MethodMirror> getDirectMethods() {
        return Collections.emptyList();
    }
    @Override
    public List<FieldMirror> getDirectFields() {
        return Collections.emptyList();
    }
    @Override
    public List<TypeParameterMirror> getTypeParameters() {
        return Collections.emptyList();
    }
    @Override
    public List<ClassMirror> getDirectInnerClasses() {
        return Collections.emptyList();
    }
    @Override
    public TypeMirror getSuperclass() {
        return null;
    }
    @Override
    public ClassMirror getEnclosingClass() {
        return null;
    }
    @Override
    public MethodMirror getEnclosingMethod() {
        return null;
    }
    @Override
    public List<TypeMirror> getInterfaces() {
        return Collections.emptyList();
    }
    @Override
    public boolean isCeylonToplevelAttribute() {
        return false;
    }

    @Override
    public boolean isCeylonToplevelObject() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isCeylonToplevelMethod() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isLoadedFromSource() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isJavaSource() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isFinal() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public String getCacheKey(Module module) {
        // TODO Auto-generated method stub
        return null;
    }
    
}

public class JDTClass implements ClassMirror, IBindingProvider {
    public static final ClassMirror UNKNOWN_CLASS = new UnknownClassMirror();
    
    WeakReference<ReferenceBinding> bindingRef;
    private PackageMirror pkg;
    private TypeMirror superclass;
    private List<MethodMirror> methods;
    private List<TypeMirror> interfaces;
    private Map<String, AnnotationMirror> annotations;
    private List<TypeParameterMirror> typeParams;
    private List<FieldMirror> fields;
    private String qualifiedName;
    private String simpleName;
    private boolean superClassSet = false;
    private List<ClassMirror> innerClasses;
    private String cacheKey;
    private JDTMethod enclosingMethod;
    private boolean enclosingMethodSet;
    private JDTClass enclosingClass;
    private boolean enclosingClassSet;
  
    private boolean isPublic;
    
    private IType type = null;
    private boolean isInterface;
    private boolean isAbstract;
    private boolean isProtected;
    private boolean isDefaultAccess;
    private boolean isInnerType; 
    private boolean isLocalType;
    private boolean isStatic;
    private boolean isFinal;
    private boolean isEnum;
    private String fileName;
    private boolean isBinary;
    private boolean isAnonymous;
    private boolean isJavaSource;
    private String javaModelPath;
    private String fullPath;
    private boolean isAnnotationType;
    private char[] bindingKey;
    
    /*
     *  the klass parameter should not be null
     *  the type parameter might be null (in case of a 
     *  MissingBinaryType). In such a case, take care of 
     *  setting in the constructor all the lazy values calculated 
     *  from the type.
     */
    public JDTClass(ReferenceBinding klass, IType type) {
        this.type = type;
        bindingRef = new WeakReference<ReferenceBinding>(klass);
        pkg = new JDTPackage(klass.getPackage());
        simpleName = new String(klass.sourceName());
        qualifiedName = JDTUtils.getFullyQualifiedName(klass);
        isPublic = klass.isPublic();
        isInterface = klass.isInterface();
        isAbstract = klass.isAbstract();
        isProtected = klass.isProtected();
        isDefaultAccess = klass.isDefault();
        isLocalType = klass.isLocalType();
        isStatic = (klass.modifiers & ClassFileConstants.AccStatic) != 0;
        isFinal = klass.isFinal();
        isEnum = klass.isEnum();
        isBinary = klass.isBinaryBinding();
        isAnonymous = klass.isAnonymousType();
        isJavaSource = (klass instanceof SourceTypeBinding) && new String(((SourceTypeBinding) klass).getFileName()).endsWith(".java");
        isAnnotationType = klass.isAnnotationType();
        bindingKey = klass.computeUniqueKey();

        char[] bindingFileName = klass.getFileName();
        int start = CharOperation.lastIndexOf('/', bindingFileName) + 1;
        if (start == 0 || start < CharOperation.lastIndexOf('\\', bindingFileName))
            start = CharOperation.lastIndexOf('\\', bindingFileName) + 1;
        fileName = new String(CharOperation.subarray(bindingFileName, start, -1));
        
        int jarFileEntrySeparatorIndex = CharOperation.indexOf(IDependent.JAR_FILE_ENTRY_SEPARATOR, bindingFileName);
        if (jarFileEntrySeparatorIndex > 0) {
            char[] jarPart = CharOperation.subarray(bindingFileName, 0, jarFileEntrySeparatorIndex);
            IJavaElement jarPackageFragmentRoot = JavaCore.create(new String(jarPart));
            String jarPath = jarPackageFragmentRoot.getPath().toOSString();
            char[] entryPart = CharOperation.subarray(bindingFileName, jarFileEntrySeparatorIndex + 1, bindingFileName.length);
            fullPath = new StringBuilder(jarPath).append("!/").append(entryPart).toString();
        } else {
            fullPath = new String(bindingFileName);
        }

        ReferenceBinding sourceOrClass = klass;
        if (! klass.isBinaryBinding()) {
            sourceOrClass = klass.outermostEnclosingType();
        }
        char[] classFullName = new char[0];
        for (char[] part : sourceOrClass.compoundName) {
            classFullName = CharOperation.concat(classFullName, part, '/');
        }
        char[][] temp = CharOperation.splitOn('.', sourceOrClass.getFileName());
        String extension = temp.length > 1 ? "." + new String(temp[temp.length-1]) : "";
        javaModelPath = new String(classFullName) + extension;
        
        if (type == null) {
            annotations = new HashMap<>();
            methods = Collections.emptyList();
            interfaces = Collections.emptyList();
            typeParams = Collections.emptyList();
            fields = Collections.emptyList();
            innerClasses = Collections.emptyList();
        }
    }

    @Override
    public AnnotationMirror getAnnotation(String annotationType) {
        if (annotations == null) {
            doWithBindings(new ActionOnClassBinding() {
                @Override
                public void doWithBinding(IType classModel, ReferenceBinding klass) {
                    annotations = JDTUtils.getAnnotations(klass.getAnnotations());
                    isInnerType = getAnnotation(AbstractModelLoader.CEYLON_CONTAINER_ANNOTATION) != null || klass.isMemberType();
                }
            });
            
        }
        return annotations.get(annotationType);
    }

    @Override
    public boolean isPublic() {
        return isPublic;
    }

    @Override
    public String getQualifiedName() {
        return qualifiedName;
    }

    @Override
    public String getFlatName() {
        // this should only make a difference if we care about local declarations
        return getQualifiedName();
    }

    @Override
    public String getName() {
        return simpleName;
    }

    @Override
    public PackageMirror getPackage() {
        return pkg;
    }

    @Override
    public boolean isInterface() {
        return isInterface;
    }

    @Override
    public boolean isAbstract() {
        return isAbstract;
    }
    
    @Override
    public boolean isProtected() {
        return isProtected;
    }
    
    @Override
    public boolean isDefaultAccess() {
        return isDefaultAccess;
    }
    
    private void doWithBindings(final ActionOnClassBinding action) {
        if (!JDTModelLoader.doWithReferenceBinding(type, bindingRef.get(), action)) {
            JDTModelLoader.doWithResolvedType(type, new ActionOnResolvedType() {
                @Override
                public void doWithBinding(ReferenceBinding classBinding) {
                    bindingRef = new WeakReference<ReferenceBinding>(classBinding);
                    action.doWithBinding(type, classBinding);
                }
            });
        }
    }
    
    
    @Override
    public List<MethodMirror> getDirectMethods() {
        if (methods == null) {
            doWithBindings(new ActionOnClassBinding() {
                @Override
                public void doWithBinding(IType classModel, ReferenceBinding klass) {
                    MethodBinding[] directMethods;
                    directMethods = klass.methods();
                    methods = new ArrayList<MethodMirror>(directMethods.length);
                    for(MethodBinding method : directMethods) {
                        if(!method.isBridge() && !method.isSynthetic() && !method.isPrivate())
                            methods.add(new JDTMethod(JDTClass.this, method));
                    }
                }
            });
        }
        return methods;
    }

    @Override
    public TypeMirror getSuperclass() {
        if (! superClassSet) {
            doWithBindings(new ActionOnClassBinding() {
                @Override
                public void doWithBinding(IType classModel, ReferenceBinding klass) {
                    if (klass.isInterface() || "java.lang.Object".equals(getQualifiedName())) {
                        superclass = null;
                    } else {
                        ReferenceBinding superClassBinding = klass.superclass();
                        if (superClassBinding != null) {
                            superClassBinding = JDTUtils.inferTypeParametersFromSuperClass(klass,
                                    superClassBinding);
                            superclass = new JDTType(superClassBinding);
                        }
                    }
                }
            });
            superClassSet = true;
        }
        return superclass;
    }

    @Override
    public List<TypeMirror> getInterfaces() {
        if (interfaces == null) {
            doWithBindings(new ActionOnClassBinding() {
                @Override
                public void doWithBinding(IType classModel, ReferenceBinding klass) {
                    ReferenceBinding[] superInterfaces = klass.superInterfaces();
                    interfaces = new ArrayList<TypeMirror>(superInterfaces.length);
                    for(ReferenceBinding superInterface : superInterfaces)
                        interfaces.add(new JDTType(superInterface));
                }
            });
        }
        return interfaces;
    }

    @Override
    public List<TypeParameterMirror> getTypeParameters() {
        if (typeParams == null) {
            doWithBindings(new ActionOnClassBinding() {
                @Override
                public void doWithBinding(IType classModel, ReferenceBinding klass) {
                    TypeVariableBinding[] typeParameters = klass.typeVariables();
                    typeParams = new ArrayList<TypeParameterMirror>(typeParameters.length);
                    for(TypeVariableBinding parameter : typeParameters)
                        typeParams.add(new JDTTypeParameter(parameter));
                }
            });
        }
        return typeParams;
    }

    private boolean isAnnotationPresent(Class<?> clazz) {
        return getAnnotation(clazz.getName()) != null;
    }
    
    @Override
    public boolean isCeylonToplevelAttribute() {
        return !isInnerClass() && isAnnotationPresent(com.redhat.ceylon.compiler.java.metadata.Attribute.class);
    }

    @Override
    public boolean isCeylonToplevelObject() {
        return !isInnerClass() && isAnnotationPresent(com.redhat.ceylon.compiler.java.metadata.Object.class);
    }

    @Override
    public boolean isCeylonToplevelMethod() {
        return !isInnerClass() && isAnnotationPresent(com.redhat.ceylon.compiler.java.metadata.Method.class);
    }

    public boolean isCeylon() {
        return isAnnotationPresent(com.redhat.ceylon.compiler.java.metadata.Ceylon.class);
    }

    @Override
    public List<FieldMirror> getDirectFields() {
        if (fields == null) {
            doWithBindings(new ActionOnClassBinding() {
                @Override
                public void doWithBinding(IType classModel, ReferenceBinding klass) {
                    FieldBinding[] directFields = klass.fields();
                    fields = new ArrayList<FieldMirror>(directFields.length);
                    for(FieldBinding field : directFields){
                        if(!field.isSynthetic() && !field.isPrivate()){
                            fields.add(new JDTField(field));
                        }
                    }
                }
            });
        }
        return fields;
    }

    @Override
    public boolean isInnerClass() {
        return isInnerType;
    }
    
    @Override
    public ClassMirror getEnclosingClass() {
        if(!enclosingClassSet){
            doWithBindings(new ActionOnClassBinding() {
                @Override
                public void doWithBinding(IType classModel, ReferenceBinding klass) {
                    ReferenceBinding enclosingType = klass.enclosingType();
                    IType enclosingTypeModel = type.getDeclaringType();
                    if (enclosingType != null) {
                        if (enclosingTypeModel == null) {
                            throw new ModelResolutionException("JDT reference binding without a JDT IType element !");
                        }
                        enclosingClass =  new JDTClass(enclosingType, enclosingTypeModel);
                    } else {
                        enclosingClass = null;
                    }
                }
            });
            enclosingClassSet = true;
        }
        return enclosingClass;
    }
    
    @Override
    public MethodMirror getEnclosingMethod() {
        if(!enclosingMethodSet){
            if(isLocalType){
                doWithBindings(new ActionOnClassBinding() {
                    @Override
                    public void doWithBinding(IType classModel, ReferenceBinding klass) {
                        LocalTypeBinding localClass = (LocalTypeBinding) klass;
                        MethodBinding enclosingMethodBinding = localClass.enclosingMethod;
                        enclosingMethod = enclosingMethodBinding != null ? new JDTMethod(JDTClass.this, enclosingMethodBinding) : null;
                    }
                });
            }
            enclosingMethodSet = true;
        }
        return enclosingMethod;
    }


    @Override
    public List<ClassMirror> getDirectInnerClasses() {
        if (innerClasses == null) {
            doWithBindings(new ActionOnClassBinding() {
                @Override
                public void doWithBinding(IType classModel, ReferenceBinding klass) {
                    ReferenceBinding[] memberTypeBindings = klass.memberTypes();
                    innerClasses = new ArrayList<ClassMirror>(memberTypeBindings.length);
                    for(ReferenceBinding memberTypeBinding : memberTypeBindings) {
                        ReferenceBinding classBinding = memberTypeBinding;
                        IType classTypeModel = JDTModelLoader.toType(classBinding);
                        innerClasses.add(new JDTClass(classBinding, classTypeModel));
                    }
                }
            });
        }
        return innerClasses;
    }

    @Override
    public boolean isStatic() {
        return isStatic;
    }

    @Override
    public boolean isFinal() {
        return isFinal;
    }
    
    @Override
    public boolean isEnum() {
        return isEnum;
    }

    public String getFileName() {
        return fileName;
    }

    public boolean isBinary() {
        return isBinary;
    }

    @Override
    public boolean isLoadedFromSource() {
        return false;
    }

    @Override
    public boolean isAnonymous() {
        return isAnonymous;
    }

    @Override
    public boolean isJavaSource() {
        return isJavaSource;
    }
    
    public String getJavaModelPath() {
        return javaModelPath;
    }

    public String getFullPath() {
        return fullPath;
    }

    @Override
    public boolean isAnnotationType() {
        return isAnnotationType;
    }

    @Override
    public boolean isLocalClass() {
        return getAnnotation(AbstractModelLoader.CEYLON_LOCAL_CONTAINER_ANNOTATION) != null 
                || isLocalType;
    }
    
    @Override
    public char[] getBindingKey() {
        return bindingKey;
    }

    @Override
    public String getCacheKey(Module module) {
        if(cacheKey == null){
            String className = getQualifiedName();
            cacheKey = AbstractModelLoader.getCacheKeyByModule(module, className);
        }
        return cacheKey;
    }

    public IType getType() {
        return type;
    }
}