package be.cytomine.ontology

import be.cytomine.CytomineDomain
import be.cytomine.Exception.AlreadyExistException
import be.cytomine.Exception.WrongArgumentException
import be.cytomine.project.Project
import be.cytomine.security.SecUser
import grails.converters.JSON

class AnnotationTerm extends CytomineDomain implements Serializable {

    Annotation annotation
    Term term
    SecUser user

    static constraints = {
    }

    String toString() {
        "[" + this.id + " <" + annotation + "," + term + "," + user + ">]"
    }

    static AnnotationTerm link(Annotation annotation, Term term,SecUser user) {
        if (!annotation) throw new WrongArgumentException("Annotation cannot be null")
        if (!term) throw new WrongArgumentException("Term cannot be null")
        if (!user) throw new WrongArgumentException("User cannot be null")
        def annotationTerm = AnnotationTerm.findWhere('annotation': annotation, 'term': term,'user': user)
        if (annotationTerm) throw new AlreadyExistException("Annotation - term already exist")
        //Annotation.withTransaction {
        if (!annotationTerm) {
            annotationTerm = new AnnotationTerm(user: user)
            annotation?.addToAnnotationTerm(annotationTerm)
            term?.addToAnnotationTerm(annotationTerm)
            annotation.refresh()
            term.refresh()
            annotationTerm.save(flush: true)
        } else throw new WrongArgumentException("Annotation " + annotation.id + " and term " + term.id + " are already mapped with user " + user.id)
        //}
        return annotationTerm
    }

    static void unlink(Annotation annotation, Term term,SecUser user) {

        if (!annotation) throw new WrongArgumentException("Annotation cannot be null")
        if (!term) throw new WrongArgumentException("Term cannot be null")
        if (!user) throw new WrongArgumentException("User cannot be null")
        def annotationTerm = AnnotationTerm.findWhere('annotation': annotation, 'term': term, 'user': user)
        if (!annotationTerm) throw new WrongArgumentException("Annotation - term - user not exist")

        if (annotationTerm) {
            annotation?.removeFromAnnotationTerm(annotationTerm)
            term?.removeFromAnnotationTerm(annotationTerm)
            annotation.refresh()
            term.refresh()
            annotationTerm.delete(flush: true)
        }
    }

    static AnnotationTerm createFromDataWithId(json) {
        def domain = createFromData(json)
        try {domain.id = json.id} catch (Exception e) {}
        return domain
    }

    static AnnotationTerm createFromData(jsonAnnotationTerm) {
        def annotationTerm = new AnnotationTerm()
        getFromData(annotationTerm, jsonAnnotationTerm)
    }

    static AnnotationTerm getFromData(annotationTerm, jsonAnnotationTerm) {
        annotationTerm.annotation = Annotation.get(jsonAnnotationTerm.annotation.toString())
        annotationTerm.term = Term.get(jsonAnnotationTerm.term.toString())
        annotationTerm.user = SecUser.get(jsonAnnotationTerm.user.toString())
        if (!annotationTerm.annotation) throw new WrongArgumentException("Annotation ${jsonAnnotationTerm.annotation.toString()} doesn't exist!")
        if (!annotationTerm.term) throw new WrongArgumentException("Term ${jsonAnnotationTerm.term.toString()} doesn't exist!")
        if (!annotationTerm.user) throw new WrongArgumentException("User ${jsonAnnotationTerm.user.toString()} doesn't exist!")
        return annotationTerm;
    }


    def getCallBack() {
        HashMap<String, Object> callback = new HashMap<String, Object>();
        callback.put("annotationID", this.annotation.id)
        callback.put("termID", this.term.id)
        callback.put("imageID", this.annotation.image.id)
    }

    static void registerMarshaller(String cytomineBaseUrl) {
        println "Register custom JSON renderer for " + AnnotationTerm.class
        JSON.registerObjectMarshaller(AnnotationTerm) {
            def returnArray = [:]
            //returnArray['class'] = it.class
            returnArray['id'] = it.id
            returnArray['annotation'] = it.annotation?.id
            returnArray['term'] = it.term?.id
            returnArray['user'] = it.user?.id
            return returnArray
        }
    }

     public Project projectDomain() {
        return annotation.image.project
    }
}
