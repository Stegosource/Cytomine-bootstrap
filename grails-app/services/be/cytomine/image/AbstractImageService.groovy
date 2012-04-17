package be.cytomine.image

import be.cytomine.Exception.CytomineException
import be.cytomine.Exception.ObjectNotFoundException
import be.cytomine.ModelService
import be.cytomine.command.AddCommand
import be.cytomine.command.DeleteCommand
import be.cytomine.command.EditCommand
import be.cytomine.image.server.ImageProperty
import be.cytomine.image.server.RetrievalServer
import be.cytomine.ontology.Annotation
import be.cytomine.project.Project
import be.cytomine.security.Group
import be.cytomine.security.User
import grails.orm.PagedResultList
import org.codehaus.groovy.grails.web.json.JSONObject
import be.cytomine.command.Transaction
import be.cytomine.image.server.Storage
import be.cytomine.image.server.StorageAbstractImage
import be.cytomine.security.SecUser

class AbstractImageService extends ModelService {

    static transactional = false
    def commandService
    def cytomineService
    def imagePropertiesService
    def responseService
    def domainService
    def transactionService
    def storageService

    def list() {
        return AbstractImage.list()
    }

    AbstractImage read(def id) {
        return AbstractImage.read(id)
    }

    AbstractImage get(def id) {
        return AbstractImage.get(id)
    }

    def list(Project project) {
        project.abstractimages()
    }

    def list(Group group) {
        group.abstractimages()
    }

    def list(SecUser user, def page, def limit, def sortedRow, def sord, def filename, def dateStart, def dateStop) {
        def data = [:]

        log.info "page=" + page + " limit=" + limit + " sortedRow=" + sortedRow + " sord=" + sord

        if (page || limit || sortedRow || sord) {
            int pg = Integer.parseInt(page) - 1
            int max = Integer.parseInt(limit)
            int offset = pg * max

            String filenameSearch = filename ?: ""
            Date dateAddedStart = dateStart ? new Date(Long.parseLong(dateStart)) : new Date(0)
            Date dateAddedStop = dateStop ? new Date(Long.parseLong(dateStop)) : new Date(8099, 11, 31) //bad code...another way to keep the max date?

            log.info "filenameSearch=" + filenameSearch + " dateAddedStart=" + dateAddedStart + " dateAddedStop=" + dateAddedStop

            PagedResultList results = user.abstractimage(max, offset, sortedRow, sord, filenameSearch, dateAddedStart, dateAddedStop)
            data.page = page + ""
            data.records = results.totalCount
            data.total = Math.ceil(results.totalCount / max) + "" //[100/10 => 10 page] [5/15
            data.rows = results.list
        }
        else {
            data = user?.abstractimages()
        }
        return data
    }


    def add(def json) throws CytomineException {
        Transaction transaction = transactionService.start()

        SecUser currentUser = cytomineService.getCurrentUser()

        def res = executeCommand(new AddCommand(user: currentUser), json)
        //AbstractImage abstractImage = retrieve(res.data.abstractimage)
        AbstractImage abstractImage = res.object
        Group group = Group.findByName(currentUser.getUsername())
        AbstractImageGroup.link(abstractImage, group)
        json.storage.each { storageID ->
            Storage storage = storageService.read(storageID)
            StorageAbstractImage.link(storage, abstractImage)
        }
        imagePropertiesService.extractUseful(abstractImage)
        //Stop transaction
        transactionService.stop()

        return res


    }

    def update(def domain,def json) throws CytomineException {
        Transaction transaction = transactionService.start()
        SecUser currentUser = cytomineService.getCurrentUser()
        def res = executeCommand(new EditCommand(user: currentUser), json)
        AbstractImage abstractImage = res.object
        StorageAbstractImage.findAllByAbstractImage(abstractImage).each { storageAbstractImage ->
            StorageAbstractImage.unlink(storageAbstractImage.storage, abstractImage)
        }
        json.storage.each { storageID ->
            Storage storage = storageService.read(storageID)
            StorageAbstractImage.link(storage, abstractImage)
        }


        //Stop transaction
        transactionService.stop()

        return res
    }

    def delete(def domain,def json) throws CytomineException {
        Transaction transaction = transactionService.start()
        SecUser currentUser = cytomineService.getCurrentUser()
        AbstractImage abstractImage = AbstractImage.read(json.id)
        Group group = Group.findByName(currentUser.getUsername())
        AbstractImageGroup.unlink(abstractImage, group)
        StorageAbstractImage.findAllByAbstractImage(abstractImage).each { storageAbstractImage ->
            StorageAbstractImage.unlink(storageAbstractImage.storage, storageAbstractImage.abstractImage)
        }
        def res =  executeCommand(new DeleteCommand(user: currentUser), json)

        //Stop transaction
        transactionService.stop()

        return res
    }


    def metadata(def id) {
        AbstractImage image = AbstractImage.read(id)
        def url = new URL(image.getMetadataURL())
        return url.text
    }

    def imageProperties(def id) {
        AbstractImage image = AbstractImage.read(id)
        if (image.imageProperties.isEmpty()) {
            imagePropertiesService.populate(image)
        }
        return image.imageProperties
    }

    def imageProperty(def imageproperty) {
        return ImageProperty.findById(imageproperty)
    }


    def imageservers(def id) {
        AbstractImage image = AbstractImage.read(id)
        def urls = image.getImageServers().collect { it.getZoomifyUrl() + image.getPath() + "/" }
        def result = [:]
        result.imageServersURLs = urls
        return result
    }

    def thumb(def id) {
        AbstractImage image = AbstractImage.read(id)
        try {
            return image.getThumbURL()
        } catch (Exception e) {
            log.error("GetThumb:" + e);
        }
    }

    def crop(Annotation annotation, def zoom) {
        if (zoom)
            return annotation.getCropURL(zoom)
        else
            return annotation.getCropURL()
    }

    def retrieval(Annotation annotation, int zoom, int maxSimilarPictures) {
        def retrievalServers = RetrievalServer.findAll()
        return retrievalServers.get(0).search(annotation.getCropURL(zoom), maxSimilarPictures)
    }

    def slidingWindow(AbstractImage abstractImage, parameters) {
        def windows = []
        int windowWidth = parameters.width
        int windowHeight = parameters.height
        int stepX = parameters.width * (1 - parameters.overlapX)
        int stepY = parameters.height * (1 - parameters.overlapY)
        for (int y = 0; y < abstractImage.getHeight(); y +=  stepY) {
            for (int x = 0; x < abstractImage.getWidth(); x += stepX) {
                int x_window = x
                int y_window =  y
                int width = windowWidth + Math.min(0, (abstractImage.getWidth() - (x_window + windowWidth)))
                int height = windowHeight + Math.min(0, (abstractImage.getHeight() - (y_window + windowHeight)))
                int invertedY =  abstractImage.getHeight() - y_window //for IIP
                String url = abstractImage.getCropURL(x_window, invertedY, width, height)
                windows << [ x : x_window, y : y_window, width : width, height : height, image : url]
            }
        }
        windows
    }

    /**
     * create domain which was previously deleted
     * @param json domain info

     * @param printMessage print message or not
     * @return response
     */
    def create(JSONObject json, boolean printMessage) {
        create(AbstractImage.createFromDataWithId(json), printMessage)
    }

    def create(AbstractImage domain, boolean printMessage) {
        //Save new object
        domainService.saveDomain(domain)
        //Build response message
        return responseService.createResponseMessage(domain, [domain.id, domain.filename], printMessage, "Add", domain.getCallBack())
    }
    /**
     * Destroy domain which was previously added
     * @param json domain info

     * @param printMessage print message or not
     * @return response
     */
    def destroy(JSONObject json, boolean printMessage) {
        //Get object to delete
        destroy(AbstractImage.get(json.id), printMessage)
    }

    def destroy(AbstractImage domain, boolean printMessage) {
        //Build response message
        def response = responseService.createResponseMessage(domain, [domain.id, domain.filename], printMessage, "Delete", domain.getCallBack())
        //Delete object
        domainService.deleteDomain(domain)
        return response
    }

    /**
     * Edit domain which was previously edited
     * @param json domain info
     * @param printMessage print message or not
     * @return response
     */
    def edit(JSONObject json, boolean printMessage) {
        //Rebuilt previous state of object that was previoulsy edited
        edit(fillDomainWithData(new AbstractImage(), json), printMessage)
    }

    def edit(AbstractImage domain, boolean printMessage) {
        //Build response message
        def response = responseService.createResponseMessage(domain, [domain.id, domain.filename], printMessage, "Edit", domain.getCallBack())
        //Save update
        domainService.saveDomain(domain)
        return response
    }

    /**
     * Create domain from JSON object
     * @param json JSON with new domain info
     * @return new domain
     */
    AbstractImage createFromJSON(def json) {
        return AbstractImage.createFromData(json)
    }

    /**
     * Retrieve domain thanks to a JSON object
     * @param json JSON with new domain info
     * @return domain retrieve thanks to json
     */
    def retrieve(JSONObject json) {
        AbstractImage image = AbstractImage.get(json.id)
        if (!image) throw new ObjectNotFoundException("Image " + json.id + " not found")
        return image
    }


}
