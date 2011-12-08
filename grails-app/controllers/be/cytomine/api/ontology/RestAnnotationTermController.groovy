package be.cytomine.api.ontology

import be.cytomine.Exception.CytomineException
import be.cytomine.api.RestController
import be.cytomine.image.ImageInstance
import be.cytomine.ontology.Annotation
import be.cytomine.ontology.Term
import be.cytomine.security.User
import grails.converters.JSON

class RestAnnotationTermController extends RestController {

    def termService
    def annotationService
    def annotationTermService

    def listTermByAnnotation = {
        if (params.idannotation == "undefined") responseNotFound("Annotation Term", "Annotation", params.idannotation)
        else {
            Annotation annotation = annotationService.read(params.idannotation)
            if (annotation && !params.idUser) responseSuccess(annotationTermService.list(annotation))
            else if (annotation && params.idUser) {
                User user = User.read(params.idUser)
                if (user) responseSuccess(termService.list(annotation, user))
                else responseNotFound("Annotation Term", "User", params.idUser)
            }
            else responseNotFound("Annotation Term", "Annotation", params.idannotation)
        }

    }

    def listAnnotationTermByUserNot = {
        if (params.idannotation == "undefined") responseNotFound("Annotation Term", "Annotation", params.idannotation)
        else {
            Annotation annotation = annotationService.read(params.idannotation)
            if (annotation != null && params.idNotUser) {
                User user = User.read(params.idNotUser)
                if (user) responseSuccess(annotationTermService.listNotUser(annotation, user))
                else responseNotFound("Annotation Term", "User", params.idUser)
            }
        }
    }

    def listAnnotationByProjectAndImageInstance = {
        Term term = Term.read(params.idterm)
        def annotations = []
        Annotation.findAllByImage(ImageInstance.read(params.idimageinstance)).each { annotation ->
            annotation.annotationTerm.each { annotationTerm ->
                if (annotationTerm.getTerm() == term) annotations << annotation
            }
        }
        responseSuccess(annotations)
    }

    def show = {
        Annotation annotation = annotationService.read(params.idannotation)
        Term term = termService.read(params.idterm)

        if (!annotation) responseNotFound("Annotation", params.idannotation)
        if (!term) responseNotFound("Term", params.idterm)
        else {
            if (params.idUser && User.read(params.idUser)) {
                def annoterm = annotationTermService.read(annotation, term, User.read(params.idUser))
                if (annoterm) responseSuccess(annoterm)
                else responseNotFound("Annotation Term", "Term", "Annotation", "User", params.idterm, params.idannotation, params.idUser)
            } else {
                def annoterm = annotationTermService.read(annotation, term, null)
                if (annoterm) responseSuccess(annoterm)
                else responseNotFound("Annotation Term", "Term", "Annotation", params.idterm, params.idannotation)
            }

        }
    }

    def add = {
        add(annotationTermService, request.JSON)
    }

    def delete = {
        def json = JSON.parse("{idannotation: $params.idannotation, idterm: $params.idterm}")
        delete(annotationTermService, json)
    }

    /**
     * Add annotation-term for an annotation and delete all annotation-term that where already map with this annotation by this user
     */
    def addWithDeletingOldTerm = {
        try {
            def result = annotationTermService.addWithDeletingOldTerm(params.idannotation, params.idterm)
            responseResult(result)
        } catch (CytomineException e) {
            log.error(e)
            response([success: false, errors: e.message], e.code)
        } finally {
            transactionService.stopIfTransactionInProgress()
        }
    }

}