var OntologyPanelView = Backbone.View.extend({
    $tree: null,
    $infoOntology: null,
    $infoTerm: null,
    $panel: null,
    $addTerm: null,
    $editTerm: null,
    $deleteTerm: null,

    initialize: function (options) {
        this.container = options.container;
    },
    render: function () {
        var self = this;
        self.$panel = $(".ontology" + self.model.id);
        self.$tree = self.$panel.find("#treeontology-" + self.model.id);
        self.$infoOntology = self.$panel.find("#infoontology-" + self.model.id);
        self.$infoTerm = self.$panel.find("#infoterm-" + self.model.id);

        self.$addTerm = self.$panel.find('#dialog-add-ontology-term');
        self.$editTerm = self.$panel.find('#dialog-edit-ontology-term');
        self.$deleteTerm = self.$panel.find('#dialogsTerm');

        self.buildOntologyTree();
        self.buildInfoOntologyPanel();

        self.initEvents();

        return this;
    },
    initEvents: function () {
        var self = this;
        console.log("initEvents:" + self.model.id);
        $("#buttonAddTerm" + this.model.id).click(function () {
            self.addTerm();
        });
        $("#buttonEditTerm" + this.model.id).click(function () {
            self.editTerm();
        });
        $("#buttonDeleteTerm" + this.model.id).click(function () {
            self.deleteTerm();
        });
        $("#buttonEditOntology" + this.model.id).click(function () {
            self.editOntology();
        });
        $("#buttonDeleteOntology" + this.model.id).click(function () {
            self.deleteOntology();
        });
    },
    refresh: function () {
        var self = this;
        self.container.refresh(self.model.id);
    },

    clear: function () {
        var self = this;
        self.$panel.empty();
        require([
            "text!application/templates/ontology/OntologyTabContent.tpl.html"
        ],
            function (tpl) {
                self.$panel.replaceWith(_.template(tpl, { id: self.model.get("id"), name: self.model.get("name")}));
                return this;
            });

        return this;
    },

    getCurrentTermId: function () {
        var node = this.$tree.dynatree("getActiveNode");
        if (node == null) {
            return null;
        }
        else {
            return node.data.id;
        }
    },

    addTerm: function () {
        var self = this;
        self.$addTerm.remove();

        new OntologyAddOrEditTermView({
            ontologyPanel: self,
            el: self.el,
            ontology: self.model,
            model: null //add component so no term
        }).render();
    },

    editTerm: function () {
        var self = this;
        console.log("OntologyPanelView.editTerm");
        self.$editTerm.remove();

        var node = self.$tree.dynatree("getActiveNode");

        if (node == null) {
            window.app.view.message("Term", "You have to select a term first", "error");
            return;
        }

        new TermModel({id: node.data.id}).fetch({
            success: function (model, response) {
                new OntologyAddOrEditTermView({
                    ontologyPanel: self,
                    el: self.el,
                    model: model,
                    ontology: self.model
                }).render();
            }});
        return false;
    },
    deleteTerm: function () {
        var self = this;
        var idTerm = self.getCurrentTermId();
        var term = window.app.models.terms.get(idTerm);
        self.buildDeleteTermConfirmDialog(term);
    },
    editOntology: function () {
        var self = this;
        $('#editontology').remove();
        self.editOntologyDialog = new EditOntologyDialog({ontologyPanel: self, el: self.el, model: self.model}).render();
    },
    deleteOntology: function () {
        var self = this;
        require(["text!application/templates/ontology/OntologyDeleteConfirmDialog.tpl.html"], function (tpl) {
            new ConfirmDialogView({
                el: '#dialogsDeleteOntologyAccept',
                template: _.template(tpl, {ontology: self.model.get('name')}),
                dialogAttr: {
                    backdrop: false,
                    dialogID: '#delete-ontology-confirm'
                }
            }).render();

            $('#deleteOntologyButton').click(function () {
                new TaskModel({project: self.model.id}).save({}, {
                         success: function (taskResponse, response) {
                             var task = taskResponse.get('task');

                             console.log("task"+task.id);
                             var timer = window.app.view.printTaskEvolution(task, $("#deleteOntologyDialogContent"), 1000);


                             new OntologyModel({id: self.model.id,task: task.id}).destroy(
                                 {
                                     success: function (model, response) {
                                         window.app.view.message("Project", response.message, "success");
                                         self.container.refresh(null)
                                         clearInterval(timer);
                                         $('#delete-ontology-confirm').modal("hide");
                                         $('#delete-ontology-confirm').remove();

                                     },
                                     error: function (model, response) {
                                         window.app.view.message("Ontology", "Errors!", "error");
                                         clearInterval(timer);
                                         var json = $.parseJSON(response.responseText);
                                         window.app.view.message("Ontology", json.errors[0], "error");
                                     }
                                 }
                             );
                             return false;
                         },
                         error: function (model, response) {
                             var json = $.parseJSON(response.responseText);
                             window.app.view.message("Task", json.errors, "error");
                         }
                     }
                 );
                return false;
            });

            $('#closeDeleteOntologyDialog').click(function () {
                $('#delete-ontology-confirm').modal('hide');
                $('#delete-ontology-confirm').modal('remove');
                return false;
            });

        });
    },
//    refuseDeleteOntology: function (numberOfProject) {
//        var self = this;
//        require(["text!application/templates/ontology/OntologyDeleteRefuseDialog.tpl.html"], function (tpl) {
//            var dialog = new ConfirmDialogView({
//                el: '#dialogsDeleteOntologyRefuse',
//                template: _.template(tpl, {name: self.model.get('name'), numberOfProject: numberOfProject}),
//                dialogAttr: {
//                    backdrop: false,
//                    dialogID: '#delete-ontology-refuse'
//                }
//            }).render();
//            $("#deleteRefuseOntologyButton").click(function () {
//                $('#delete-ontology-refuse').modal("hide");
//                $('#delete-ontology-refuse').remove();
//                return false;
//            });
//        });
//    },
//    acceptDeleteOntology: function () {
//
//    },
    selectTerm: function (idTerm) {
        var self = this;
        self.$tree.dynatree("getTree").activateKey(idTerm);
    },
    buildDeleteTermConfirmDialog: function (term) {
        var self = this;
        require(["text!application/templates/ontology/OntologyDeleteTermConfirmDialog.tpl.html"], function (tpl) {
            var dialog = new ConfirmDialogView({
                el: '#dialogsTerm',
                template: _.template(tpl, {term: term.get('name'), ontology: self.model.get('name')}),
                dialogAttr: {
                    backdrop: false,
                    dialogID: '#delete-term-confirm'
                }
            }).render();
            $('#closeDeleteTermDialog').click(function () {
                $('#delete-term-confirm').modal('hide');
                $('#delete-term-confirm').remove();
                return false;
            });
            $('#deleteTermButton').click(function () {
                self.removeTerm(term, '#delete-term-confirm');
                return false;
            });
        });
    },
    /**
     * Delete a term which can have relation but no annotation
     * @param term term that must be deleted
     */
    removeTerm: function (term, dialogIdentifier) {
        var self = this;
        new TermModel({id: term.id}).destroy({
            success: function (model, response) {
                window.app.view.message("Term", response.message, "success");
                self.refresh();
                $(dialogIdentifier).modal('hide');
                $(dialogIdentifier).remove();
            },
            error: function (model, response) {
                var json = $.parseJSON(response.responseText);
                $("#delete-term-error-message").empty();
                $("#delete-term-error-label").show();
                $("#delete-term-error-message").append(json.errors)
            }});
    },
    buildInfoOntologyPanel: function () {
        var self = this;
        self.$infoOntology.empty();
        var buttonId = "seeUserOntology-" + self.model.id;
        var tpl = _.template("" +
            "<div id='userontologyinfo-<%= id %>' style='padding:5px;'>" +
            "<ul><li><b>Ontology</b> : <%= ontologyName %></li>" +
            "<li><b>Users</b> : <button id='" + buttonId + "' class='btn'>See users list</button></li><li class='projectsLinked'></li></ul></div>", { id: self.model.id, ontologyName: self.model.get('name')});
        self.$infoOntology.html(tpl);

        $("#" + buttonId).click(function () {
            console.log("open ontology user " + self.model.id)
            new ontologyUsersDialog({model: self.model, el: $("#ontology")}).render();
        });


        var projectsLinked = []
        _.each(self.model.get("projects"), function (project) {
            var tpl = _.template("<a href='#tabs-dashboard-<%=   idProject %>'><%=   projectName %></a>", {idProject: project.id, projectName: project.name});
            projectsLinked.push(tpl);
        });
        var tpl = _.template("<b>Projects</b> : <%=   projects %>", {projects: projectsLinked.join(", ")});
        self.$infoOntology.find('.projectsLinked').html(tpl);

    },
    buildOntologyTree: function () {
        var self = this;
        var currentTime = new Date();

        self.$tree.empty();
        $("#treeontology-" + self.model.id).dynatree({
            children: self.model.toJSON(),
            onExpand: function () {
            },
            onClick: function (node, event) {
            },
            onSelect: function (select, node) {
            },
            onActivate: function (node) {
            },
            onDblClick: function (node, event) {
            },
            onRender: function (node, nodeSpan) {
                self.$tree.find("a.dynatree-title").css("color", "black");
            },
            //generateIds: true,
            // The following options are only required, if we have more than one tree on one page:
            initId: "treeDataOntology-" + self.model.id + currentTime.getTime(),
            cookieId: "dynatree-Ontology-" + self.model.id + currentTime.getTime(),
            idPrefix: "dynatree-Ontology-" + self.model.id + currentTime.getTime() + "-",
            debugLevel: 0
        });

        self.colorizeOntologyTree();
        self.expandOntologyTree();
    },
    colorizeOntologyTree: function () {
        var self = this;
        $("#treeontology-" + self.model.id).dynatree("getRoot").visit(function (node) {
            if (node.children != null) {
                return;
            } //title is ok
            var title = node.data.title
            var color = node.data.color
            var htmlNode = "<a href='#ontology/<%=   idOntology %>/<%=   idTerm %>' onClick='window.location.href = this.href;'><%=   title %> <span style='background-color:<%= color %>'>&nbsp;&nbsp;&nbsp;&nbsp;</span></a>";
            var nodeTpl = _.template(htmlNode, {idOntology: self.model.id, idTerm: node.data.id, title: title, color: color});
            node.setTitle(nodeTpl);
        });
    },
    expandOntologyTree: function () {
        var self = this;
        //expand all nodes
        $("#treeontology-" + self.model.id).dynatree("getRoot").visit(function (node) {
            node.expand(true);
        });
    }
});