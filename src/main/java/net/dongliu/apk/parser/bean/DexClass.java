/*
 * Copyright 2021 Clifford Liu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dongliu.apk.parser.bean;

import net.dongliu.apk.parser.struct.dex.DexClassStruct;

import javax.annotation.Nullable;

/**
 * @author dongliu
 */
public class DexClass implements Cloneable {
    /**
     * the class name
     */
    private String classType;
    private String superClass;
    private int accessFlags;

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public DexClass() {
    }

    public DexClass(String classType, String superClass, int accessFlags) {
        this.classType = classType;
        this.superClass = superClass;
        this.accessFlags = accessFlags;
    }

    public void setClassType(String classType) {
        this.classType = classType;
    }

    public void setSuperClass(String superClass) {
        this.superClass = superClass;
    }

    public void setAccessFlags(int accessFlags) {
        this.accessFlags = accessFlags;
    }

    public String getPackageName() {
        String packageName = classType;
        if (packageName.length() > 0) {
            if (packageName.charAt(0) == 'L') {
                packageName = packageName.substring(1);
            }
        }
        if (packageName.length() > 0) {
            int idx = classType.lastIndexOf('/');
            if (idx > 0) {
                packageName = packageName.substring(0, classType.lastIndexOf('/') - 1);
            } else if (packageName.charAt(packageName.length() - 1) == ';') {
                packageName = packageName.substring(0, packageName.length() - 1);
            }
        }
        return packageName.replace('/', '.');
    }

    public String getClassType() {
        return classType;
    }

    @Nullable
    public String getSuperClass() {
        return superClass;
    }

    public boolean isInterface() {
        return (this.accessFlags & DexClassStruct.ACC_INTERFACE) != 0;
    }

    public boolean isEnum() {
        return (this.accessFlags & DexClassStruct.ACC_ENUM) != 0;
    }

    public boolean isAnnotation() {
        return (this.accessFlags & DexClassStruct.ACC_ANNOTATION) != 0;
    }

    public boolean isPublic() {
        return (this.accessFlags & DexClassStruct.ACC_PUBLIC) != 0;
    }

    public boolean isProtected() {
        return (this.accessFlags & DexClassStruct.ACC_PROTECTED) != 0;
    }

    public boolean isStatic() {
        return (this.accessFlags & DexClassStruct.ACC_STATIC) != 0;
    }

    @Override
    public String toString() {
        return classType;
    }
}
