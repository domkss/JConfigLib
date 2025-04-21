/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2025 Dominik Kiss
 * Repository: https://github.com/domkss/JConfigLib
 */

package dev.domkss.jconfiglib;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("dev.domkss.jconfiglib.ConfigField")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ConfigFieldProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(ConfigField.class)) {
            if (element.getKind() != ElementKind.FIELD) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@ConfigField can only be applied to fields", element);
                continue;
            }

            VariableElement var = (VariableElement) element;
            TypeMirror type = var.asType();

            String typeName = type.toString();
            // Check for boxed types (Wrapper classes)
            if (isSupportedBasicTypes(typeName)) {
                continue; // Allow boxed types like Integer, Long, etc.
            }

            // Check for Lists that contain primitives or boxed primitives
            if (typeName.startsWith("java.util.List")) {
                if (!isValidListType(type)) {
                    error(var, "@ConfigField is only supported on Lists that contain primitive or boxed primitive types.");
                }
                continue; // Allowed list type
            }

            // Check for Maps where the key is String and value is primitive/boxed primitive
            if (typeName.startsWith("java.util.Map")) {
                if (!isValidMapType(type)) {
                    error(var, "@ConfigField is only supported on Maps where the key is String and the value is primitive or boxed primitive.");
                }
                continue; // Allowed map type
            }


            // Not supported
            error(var, "@ConfigField is not supported on this type Found: " + typeName);
        }
        return true;
    }

    private void error(Element e, String msg) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
    }

    private boolean isSupportedBasicTypes(String typeName) {
        return typeName.equals("java.lang.Integer") ||
                typeName.equals("java.lang.Long") ||
                typeName.equals("java.lang.Float") ||
                typeName.equals("java.lang.Double") ||
                typeName.equals("java.lang.Short") ||
                typeName.equals("java.lang.Byte") ||
                typeName.equals("java.lang.Character") ||
                typeName.equals("java.lang.Boolean") ||
                typeName.equals("java.math.BigInteger") ||
                typeName.equals("java.math.BigDecimal") ||
                typeName.equals("java.util.Date") ||
                typeName.equals("int") ||
                typeName.equals("long") ||
                typeName.equals("float") ||
                typeName.equals("double") ||
                typeName.equals("short") ||
                typeName.equals("byte") ||
                typeName.equals("char") ||
                typeName.equals("boolean")||
                typeName.equals("java.lang.String");
    }

    private boolean isValidListType(TypeMirror type) {
        DeclaredType declaredType = (DeclaredType) type;
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();

        if (typeArguments.isEmpty()) {
            return false; // Empty lists are not valid
        }

        // Check the first argument of the List
        TypeMirror listTypeArgument = typeArguments.get(0);
        String listTypeName = listTypeArgument.toString();

        return isSupportedBasicTypes(listTypeName);
    }

    private boolean isValidMapType(TypeMirror type) {
        DeclaredType declaredType = (DeclaredType) type;
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();

        if (typeArguments.size() != 2) {
            return false; // Maps should have 2 type arguments (key, value)
        }

        // The first argument should be String (key)
        TypeMirror keyType = typeArguments.get(0);
        if (!keyType.toString().equals("java.lang.String")) {
            return false; // Key must be String
        }

        // The second argument should be primitive or boxed
        TypeMirror valueType = typeArguments.get(1);
        String valueTypeName = valueType.toString();

        return isSupportedBasicTypes(valueTypeName);
    }
}


