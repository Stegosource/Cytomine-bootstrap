package be.cytomine.api.security

import be.cytomine.api.RestController
import be.cytomine.image.AbstractImage
import be.cytomine.security.Group
import grails.converters.JSON

/**
 * Controller for group of users
 */
class RestGroupController extends RestController {

    def abstractImageService
    def groupService
    def transactionService

    /**
     * List all group
     */
    def list = {
        responseSuccess(groupService.list())
    }

    /**
     * List group by abstract image
     */
    def listGroupByAbstractImage = {
        if (params.idabstractimage == "undefined") {
            responseNotFound("AbstractImageGroup", "AbstractImage", params.idabstractimage)
        } else {
            AbstractImage abstractImage = abstractImageService.read(params.long('idabstractimage'))
            if (abstractImage) {
                responseSuccess(groupService.list(abstractImage))
            } else {
                responseNotFound("AbstractImageGroup", "AbstractImage", params.idabstractimage)
            }
        }
    }

    /**
     * Get a group info
     */
    def show = {
        Group group = groupService.read(params.long('id'))
        if (group) {
            responseSuccess(group)
        } else {
            responseNotFound("Group", params.id)
        }
    }

    /**
     * Add a new group
     */
    def add = {
        add(groupService, request.JSON)
    }

    /**
     * Update a group
     */
    def update = {
        update(groupService, request.JSON)
    }

    /**
     * Delete a group
     */
    def delete = {
        delete(groupService, JSON.parse("{id : $params.id}"),null)
    }
}
