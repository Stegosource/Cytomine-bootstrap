/**
 * Created by IntelliJ IDEA.
 * User: lrollus
 * Date: 7/04/11
 * Time: 15:01
 * To change this template use File | Settings | File Templates.
 */
var OntologyModel = Backbone.Model.extend({
    url:function () {
        var base = 'api/ontology';
        var format = '.json';
        if (this.isNew()) return base + format;
        return base + (base.charAt(base.length - 1) == '/' ? '' : '/') + this.id + format;
    }
});


// define our collection
var OntologyCollection = Backbone.Collection.extend({
    model:OntologyModel,
    CLASS_NAME:"be.cytomine.ontology.Ontology",
    url:function () {
        if (this.light == undefined) {
            return "api/ontology.json";
        } else {
            return "api/ontology/light.json";
        }
    },
    initialize:function (options) {
        if (options != undefined) {
            this.light = options.light;
        }
    }, comparator:function (ontology) {
        return ontology.get("name");
    }
});

