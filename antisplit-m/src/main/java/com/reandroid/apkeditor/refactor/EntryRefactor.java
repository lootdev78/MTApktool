/*
 *  Copyright (C) 2022 github.com/REAndroid
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.reandroid.apkeditor.refactor;

import com.reandroid.identifiers.ResourceIdentifier;
import com.reandroid.identifiers.TypeIdentifier;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ResValue;
import com.reandroid.arsc.value.ValueType;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.text.Normalizer;

public class EntryRefactor {
    private final TypeIdentifier mTypeIdentifier;
    private final Set<String> usedNames = new HashSet<>();
    public EntryRefactor(TypeIdentifier typeIdentifier){
        this.mTypeIdentifier = typeIdentifier;
        for (ResourceIdentifier entry : typeIdentifier.getItems()) usedNames.add(entry.getName());
    }
    public int refactorAll(){
        int result = 0;
        for(ResourceIdentifier ri : mTypeIdentifier.getItems()){
            if(!ri.isGeneratedName()){
                continue;
            }
            boolean renamed = refactor(ri);
            if(renamed){
                result ++;
            }
        }
        return result;
    }
    private boolean refactor(ResourceIdentifier entry){
        return refactorByValue(entry);
    }
    // MTAPKTool extension: name generated string resources from their values.
    private boolean refactorByValue(ResourceIdentifier resourceIdentifier){
        if (!"string".equals(resourceIdentifier.getTypeName()) || resourceIdentifier.getPackageIdentifier() == null) return false;
        ResourceEntry resource = resourceIdentifier.getPackageIdentifier().getPackageBlock().getResource(resourceIdentifier.getResourceId());
        if (resource == null) return false;
        String fallback = null, text = null;
        for (Entry entry : resource) {
            ResValue value = entry.getResValue();
            if (value == null || value.getValueType() != ValueType.STRING) continue;
            if (entry.getResConfig().isDefault()) fallback = value.getValueAsString();
            if ("en".equals(entry.getResConfig().getLanguage())) { text = value.getValueAsString(); break; }
        }
        if (text == null) text = fallback;
        if (text == null || text.isEmpty()) return false;
        String name = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (name.isEmpty()) return false;
        if (name.length() > 64) name = name.substring(0, 64).replaceAll("_+$", "");
        if (!Character.isLetter(name.charAt(0))) name = "text_" + name;
        if (usedNames.contains(name)) name += "_" + Integer.toHexString(resourceIdentifier.getResourceId());
        if (!usedNames.add(name)) return false;
        resourceIdentifier.setName(name);
        return resourceIdentifier.renameSpec();
    }
}
