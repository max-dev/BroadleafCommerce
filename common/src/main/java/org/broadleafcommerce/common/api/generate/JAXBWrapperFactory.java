/*
 * Copyright 2008-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.broadleafcommerce.common.api.generate;

import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.File;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JCatchBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldRef;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JTryBlock;
import com.sun.codemodel.JVar;
import org.apache.commons.beanutils.PropertyUtils;
import org.broadleafcommerce.common.api.APIUnwrapper;
import org.broadleafcommerce.common.api.APIWrapper;
import org.broadleafcommerce.common.api.BaseWrapper;
import org.broadleafcommerce.common.money.Money;
import org.springframework.context.ApplicationContext;

/**
 * Utilizes the jaxb sub-project for emitting java source files.
 * jaxb uses this subproject itself.
 *
 * @author Jeff Fischer
 */
public class JAXBWrapperFactory {

    public static void emitWrapper(Class<?> modelClass, File srcDir) throws Exception {
        String superWrapperName;
        Class<?> superClass = modelClass.getSuperclass();
        if (superClass.equals(Object.class)) {
            superWrapperName = BaseWrapper.class.getName();
        } else {
            superWrapperName = createWrapperClassname(superClass);
        }

        String wrapperName = modelClass.getSimpleName();
        if (wrapperName.endsWith("Impl")) {
            wrapperName = wrapperName.substring(0, wrapperName.lastIndexOf("Impl"));
        }
        String rootElement = wrapperName.toLowerCase();
        wrapperName += "Wrapper";

        JCodeModel jCodeModel = new JCodeModel();
        JPackage jp = jCodeModel._package(modelClass.getPackage().getName());
        JDefinedClass jc = jp._class(wrapperName);

        if (!modelClass.isAnnotationPresent(RESTWrapper.class)) {
           throw new RuntimeException("A RESTWrapper annotation was not found on the passed in modelClass. Unable to emit a wrapper implementation.");
        }
        RESTWrapper restWrapper = modelClass.getAnnotation(RESTWrapper.class);

        JClass wrapperSuperClass = jCodeModel.directClass(superWrapperName);
        Class<?> modelInterface = restWrapper.modelInterface();
        if (modelInterface.equals(Object.class)) {
            String modelSimpleName = modelClass.getSimpleName();
            for (Class<?> iface : modelClass.getInterfaces()) {
                if (modelSimpleName.startsWith(iface.getSimpleName())) {
                    modelInterface = iface;
                    break;
                }
            }
            if (modelInterface.equals(Object.class)) {
                modelInterface = modelClass;
            }
        }
        jc._extends(wrapperSuperClass);
        jc._implements(jCodeModel.directClass(APIWrapper.class.getName()).narrow(modelInterface));
        jc._implements(jCodeModel.directClass(APIUnwrapper.class.getName()).narrow(modelInterface));

        //@XmlRootElement(name = "product")
        jc.annotate(XmlRootElement.class).param("name", rootElement);

        //@XmlAccessorType(value = XmlAccessType.FIELD)
        jc.annotate(XmlAccessorType.class).param("value", XmlAccessType.FIELD);

        JMethod wrap = jc.method(JMod.PUBLIC, jc.owner().VOID, "wrap");
        wrap.annotate(Override.class);

        JClass runtimeException = jCodeModel.directClass(RuntimeException.class.getName());
        JTryBlock jWrapTryBlock = wrap.body()._try();
        JCatchBlock jWrapCatchBlock = jWrapTryBlock._catch(jCodeModel.directClass(Exception.class.getName()));
        JVar wrapException = jWrapCatchBlock.param("e");
        jWrapCatchBlock.body()._throw(JExpr._new(runtimeException).arg(wrapException));
        JBlock jWrapBlock = jWrapTryBlock.body();

        JVar model = wrap.param(modelInterface, "model");
        JVar request = wrap.param(HttpServletRequest.class, "request");
        JClass baseWrapper = jCodeModel.directClass(BaseWrapper.class.getName());
        JFieldRef implementationClass = JExpr.ref("implementationClass");
        jWrapBlock.assign(implementationClass, model.invoke("getClass").invoke("getName"));
        if (!superWrapperName.equals(BaseWrapper.class.getName())) {
            jWrapBlock.directStatement("super.wrap(model, request);");

            for (SuppressedElement suppressedElement : restWrapper.suppressedElements()) {
                jWrapBlock.directStatement("super." + suppressedElement.fieldName() + " = null;");
            }
        }

        JMethod unwrap = jc.method(JMod.PUBLIC, modelInterface, "unwrap");
        unwrap.annotate(Override.class);

        JTryBlock jUnwrapTryBlock = unwrap.body()._try();
        JCatchBlock jUnwrapCatchBlock = jUnwrapTryBlock._catch(jCodeModel.directClass(Exception.class.getName()));
        JVar unwrapException = jUnwrapCatchBlock.param("e");
        jUnwrapCatchBlock.body()._throw(JExpr._new(runtimeException).arg(unwrapException));
        JBlock jUnwrapBlock = jUnwrapTryBlock.body();

        unwrap.param(HttpServletRequest.class, "request");
        unwrap.param(ApplicationContext.class, "context");
        JVar unwrapped;
        if (!superWrapperName.equals(BaseWrapper.class.getName())) {
            unwrapped = jUnwrapBlock.decl(jCodeModel.ref(modelInterface), "unwrapped", JExpr._super().invoke(unwrap));
        } else {
            jUnwrapBlock.directStatement("if (implementationClass == null) throw new RuntimeException(\"Cannot unwrap object when implementationClass is null!\");");
            JClass clazz = jCodeModel.directClass(Class.class.getName());
            unwrapped = jUnwrapBlock.decl(jCodeModel.ref(modelInterface), "unwrapped", JExpr.cast(jCodeModel.ref(modelInterface), clazz.staticInvoke("forName").arg(implementationClass).invoke("newInstance")));
        }

        JClass propertyUtils = jCodeModel.directClass(PropertyUtils.class.getName());

        //declare the marked fields
        Field[] fields = modelClass.getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(RESTElement.class)) {
                //@XmlElement
                JFieldVar myField;
                if (field.getType().isPrimitive() || isBasicType(field.getType())) {
                    myField = jc.field(JMod.PROTECTED, field.getType(), field.getName());
                } else {
                    JClass myClass = jCodeModel.directClass(createWrapperClassname(field.getType()));
                    myField = jc.field(JMod.PROTECTED, myClass, field.getName());
                }
                myField.annotate(XmlElement.class);
                jWrapBlock.assign(myField, propertyUtils.staticInvoke("getProperty").arg(model).arg(field.getName()));
                jUnwrapBlock.staticInvoke(propertyUtils, "setProperty").arg(unwrapped).arg(field.getName()).arg(myField);

                /*
                TODO add code to support getting/setting for collections or maps for wrap and unwrap for collections and maps
                 */
            }
        }

        jUnwrapBlock._return(unwrapped);

        jCodeModel.build(srcDir);
    }

    private static String createWrapperClassname(Class<?> myClass) {
        String superWrapperName;
        superWrapperName = myClass.getName();
        if (superWrapperName.endsWith("Impl")) {
            superWrapperName = superWrapperName.substring(0, superWrapperName.lastIndexOf("Impl"));
        }
        superWrapperName += "Wrapper";

        return superWrapperName;
    }

    private static final HashSet<Class<?>> BASIC_TYPES = getBasicTypes();

    public static boolean isBasicType(Class<?> clazz) {
        return BASIC_TYPES.contains(clazz);
    }

    private static HashSet<Class<?>> getBasicTypes() {
        HashSet<Class<?>> ret = new HashSet<Class<?>>();
        ret.add(Boolean.class);
        ret.add(Character.class);
        ret.add(Byte.class);
        ret.add(Short.class);
        ret.add(Integer.class);
        ret.add(Long.class);
        ret.add(Float.class);
        ret.add(Double.class);

        ret.add(Date.class);
        ret.add(Money.class);
        ret.add(BigDecimal.class);
        ret.add(String.class);

        return ret;
    }
}