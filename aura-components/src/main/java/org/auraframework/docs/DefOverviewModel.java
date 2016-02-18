/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.docs;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.auraframework.annotations.Annotations.ServiceComponentModelInstance;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.DefDescriptor.DefType;
import org.auraframework.def.Definition;
import org.auraframework.ds.servicecomponent.ModelInstance;
import org.auraframework.instance.BaseComponent;
import org.auraframework.service.ContextService;
import org.auraframework.service.DefinitionService;
import org.auraframework.system.Annotations.AuraEnabled;
import org.auraframework.system.AuraContext;
import org.auraframework.throwable.quickfix.QuickFixException;
import org.auraframework.util.AuraTextUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 */
@ServiceComponentModelInstance
public class DefOverviewModel implements ModelInstance {

    private final List<Map<String, Object>> dependencies = Lists.newArrayList();

    public DefOverviewModel(ContextService contextService, DefinitionService definitionService) throws QuickFixException {

        AuraContext context = contextService.getCurrentContext();
        BaseComponent<?, ?> component = context.getCurrentComponent();

        String desc = (String) component.getAttributes().getValue("descriptor");

        DefType defType = DefType.valueOf(((String) component.getAttributes().getValue("defType")).toUpperCase());
		DefDescriptor<?> descriptor = definitionService.getDefDescriptor(desc, defType.getPrimaryInterface());

        Definition def = definitionService.getDefinition(descriptor);
		ReferenceTreeModel.assertAccess(def);
		
        Map<DefType, List<DefModel>> depsMap = Maps.newEnumMap(DefType.class);

        Set<DefDescriptor<?>> deps = Sets.newHashSet();

        def.appendDependencies(deps);

        for (DefDescriptor<?> dep : deps) {
            DefType type = dep.getDefType();

            List<DefModel> depsList = depsMap.get(type);
            if (depsList == null) {
                depsList = Lists.newArrayList();
                depsMap.put(type, depsList);
            }
            depsList.add(new DefModel(dep));

        }

        for (Entry<DefType, List<DefModel>> entry : depsMap.entrySet()) {
            List<DefModel> list = entry.getValue();
            Collections.sort(list);

            Map<String, Object> group = Maps.newHashMap();
            group.put("type", AuraTextUtil.initCap(entry.getKey().toString().toLowerCase()));
            group.put("list", list);
            dependencies.add(group);
        }
    }

    @AuraEnabled
    public List<Map<String, Object>> getDependencies() {
        return this.dependencies;
    }
}
