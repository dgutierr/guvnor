/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
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

package org.guvnor.structure.client.editors.repository.list;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.google.gwt.user.client.ui.IsWidget;
import org.guvnor.structure.client.editors.context.GuvnorStructureContext;
import org.guvnor.structure.client.editors.context.GuvnorStructureContextChangeHandler;
import org.guvnor.structure.config.SystemRepositoryChangedEvent;
import org.guvnor.structure.repositories.Repository;
import org.guvnor.structure.repositories.RepositoryEnvironmentUpdatedEvent;
import org.guvnor.structure.repositories.RepositoryService;
import org.jboss.errai.common.client.api.Caller;
import org.uberfire.backend.vfs.VFSService;
import org.uberfire.client.annotations.WorkbenchPartTitle;
import org.uberfire.client.annotations.WorkbenchPartView;
import org.uberfire.client.annotations.WorkbenchScreen;
import org.uberfire.client.callbacks.Callback;
import org.uberfire.ext.widgets.core.client.resources.i18n.CoreConstants;
import org.uberfire.lifecycle.OnShutdown;
import org.uberfire.lifecycle.OnStartup;

@Dependent
@WorkbenchScreen(identifier = "RepositoriesEditor")
public class RepositoriesPresenter
        implements GuvnorStructureContextChangeHandler,
                   HasRemoveRepositoryHandlers {

    @Inject
    private Caller<VFSService> vfsService;

    private Caller<RepositoryService> repositoryService;

    @Inject
    private Event<RepositoryEnvironmentUpdatedEvent> repositoryUpdatedEvent;

    private GuvnorStructureContext guvnorStructureContext;

    private Map<Repository, RepositoryItemPresenter> repositoryToWidgetMap = new HashMap<Repository, RepositoryItemPresenter>();
    private RepositoriesView    view;
    private HandlerRegistration changeHandlerRegistration;

    public RepositoriesPresenter() {
    }

    @Inject
    public RepositoriesPresenter( final RepositoriesView view,
                                  final GuvnorStructureContext guvnorStructureContext,
                                  final Caller<RepositoryService> repositoryService ) {
        this.view = view;
        this.guvnorStructureContext = guvnorStructureContext;
        this.repositoryService = repositoryService;

        changeHandlerRegistration = guvnorStructureContext.addGuvnorStructureContextChangeHandler( this );

        view.setPresenter( this );
    }

    @OnStartup
    public void onStartup() {
        loadContent();
    }

    @OnShutdown
    public void shutdown() {
        guvnorStructureContext.removeHandler( changeHandlerRegistration );
    }

    private void loadContent() {
        repositoryToWidgetMap.clear();
        view.clear();

        guvnorStructureContext.getRepositories( new Callback<Collection<Repository>>() {
            @Override
            public void callback( final Collection<Repository> response ) {
                for ( final Repository repo : response ) {
                    repositoryToWidgetMap.put( repo,
                                               addRepositoryItem( repo,
                                                                  guvnorStructureContext.getCurrentBranch( repo.getAlias() ) ) );
                }
            }
        } );
    }


    @WorkbenchPartTitle
    public String getTitle() {
        return CoreConstants.INSTANCE.RepositoryEditor();
    }

    @WorkbenchPartView
    public IsWidget getView() {
        return view.asWidget();
    }

    public void removeRepository( final Repository repository ) {
        if ( view.confirmDeleteRepository( repository ) ) {
            repositoryService.call().removeRepository( repository.getAlias() );
        }
    }

    @Override
    public void onNewRepositoryAdded( final Repository repository ) {
        addRepositoryItem( repository,
                           repository.getDefaultBranch() );
    }

    @Override
    public void onRepositoryDeleted( final Repository repository ) {
        final RepositoryItemPresenter repositoryItem = repositoryToWidgetMap.remove( repository );
        view.removeIfExists( repositoryItem );
    }

    private RepositoryItemPresenter addRepositoryItem( final Repository newRepository,
                                                       final String branch ) {
        final RepositoryItemPresenter repositoryItemPresenter = view.addRepository( newRepository,
                                                                                    branch );
        repositoryItemPresenter.addRemoveRepositoryCommand( this );


        return repositoryItemPresenter;
    }

    public void onSystemRepositoryChanged( @Observes final SystemRepositoryChangedEvent event ) {
        loadContent();
    }
}