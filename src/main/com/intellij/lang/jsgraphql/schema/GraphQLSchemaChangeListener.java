/*
 * Copyright (c) 2018-present, Jim Kynde Meyer
 * All rights reserved.
 * <p>
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package com.intellij.lang.jsgraphql.schema;

import com.google.common.collect.Lists;
import com.intellij.lang.jsgraphql.endpoint.psi.JSGraphQLEndpointFile;
import com.intellij.lang.jsgraphql.psi.GraphQLFile;
import com.intellij.lang.jsgraphql.psi.GraphQLFragmentDefinition;
import com.intellij.lang.jsgraphql.psi.GraphQLOperationDefinition;
import com.intellij.lang.jsgraphql.psi.GraphQLTemplateDefinition;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Tracks PSI changes that can affect declared GraphQL schemas
 */
public class GraphQLSchemaChangeListener {

    public final static Topic<GraphQLSchemaEventListener> TOPIC = new Topic<>(
            "GraphQL Schema Change Events",
            GraphQLSchemaEventListener.class,
            Topic.BroadcastDirection.TO_PARENT
    );

    public static GraphQLSchemaChangeListener getService(@NotNull Project project) {
        return ServiceManager.getService(project, GraphQLSchemaChangeListener.class);
    }


    private final Project myProject;
    private final PsiTreeChangeAdapter listener;
    private final PsiManager psiManager;

    public GraphQLSchemaChangeListener(Project project) {
        myProject = project;
        psiManager = PsiManager.getInstance(myProject);
        listener = new PsiTreeChangeAdapter() {

            private void checkForSchemaChange(PsiTreeChangeEvent event) {
                if (myProject.isDisposed()) {
                    psiManager.removePsiTreeChangeListener(listener);
                    return;
                }
                if (event.getFile() instanceof GraphQLFile) {
                    if (affectsGraphQLSchema(event)) {
                        signalSchemaChanged();
                    }
                }
                if (event.getFile() instanceof JSGraphQLEndpointFile) {
                    // always consider the schema changed when editing an endpoint file
                    signalSchemaChanged();
                }
            }

            private void signalSchemaChanged() {
                myProject.getMessageBus().syncPublisher(GraphQLSchemaChangeListener.TOPIC).onGraphQLSchemaChanged();
            }

            @Override
            public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
                checkForSchemaChange(event);
            }

            @Override
            public void childAdded(@NotNull PsiTreeChangeEvent event) {
                checkForSchemaChange(event);
            }

            @Override
            public void childRemoved(@NotNull PsiTreeChangeEvent event) {
                checkForSchemaChange(event);
            }

            @Override
            public void childMoved(@NotNull PsiTreeChangeEvent event) {
                checkForSchemaChange(event);
            }

            @Override
            public void childReplaced(@NotNull PsiTreeChangeEvent event) {
                checkForSchemaChange(event);
            }

        };
        psiManager.addPsiTreeChangeListener(listener);
    }

    /**
     * Evaluates whether the change event can affect the associated GraphQL schema
     *
     * @param event the event that occurred
     *
     * @return true if the change can affect the declared schema
     */
    private boolean affectsGraphQLSchema(PsiTreeChangeEvent event) {
        if (PsiTreeChangeEvent.PROP_FILE_NAME.equals(event.getPropertyName()) || PsiTreeChangeEvent.PROP_DIRECTORY_NAME.equals(event.getPropertyName())) {
            // renamed and moves are likely to affect schema blobs etc.
            return true;
        }
        final List<PsiElement> elements = Lists.newArrayList(event.getParent(), event.getChild(), event.getNewChild(), event.getOldChild());
        for (PsiElement element : elements) {
            if (element == null) {
                continue;
            }
            if (PsiTreeUtil.findFirstParent(element, parent -> parent instanceof GraphQLOperationDefinition || parent instanceof GraphQLFragmentDefinition || parent instanceof GraphQLTemplateDefinition) != null) {
                // edits inside query, mutation, subscription, fragment etc. don't affect the schema
                return false;
            }
        }
        // fallback to assume the schema can be affected by the edit
        return true;
    }

}
