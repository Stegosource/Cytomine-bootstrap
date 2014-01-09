package be.cytomine

import be.cytomine.security.SecUser
import grails.converters.JSON
import groovy.sql.Sql
import org.apache.log4j.Logger
import org.jsondoc.core.annotation.ApiObjectField
import org.jsondoc.core.pojo.ApiObjectFieldDoc
import org.springframework.security.acls.model.Permission

import java.lang.reflect.Field

import static org.springframework.security.acls.domain.BasePermission.*

/**
 * CytomineDomain is the parent class for all domain.
 * It allow to give an id to each instance of a domain, to get a created date,...
 */
abstract class CytomineDomain  implements Comparable{

    def springSecurityService
    def cytomineService
    def sequenceService

    static def grailsApplication
    Long id
    Date created
    Date updated

    static mapping = {
        tablePerHierarchy false
        id generator: "assigned"
        sort "id"
    }

    static constraints = {
        created nullable: true
        updated nullable: true
    }

    public beforeInsert() {
        if (!created) {
            created = new Date()
        }
        if (id == null) {
            id = sequenceService.generateID()
        }
    }

  def beforeValidate() {
      if (!created) {
          created = new Date()
      }
      if (id == null) {
          id = sequenceService.generateID()
      }
  }

    public beforeUpdate() {
        updated = new Date()
    }

    /**
     * This function check if a domain already exist (e.g. project with same name).
     * A domain that must be unique should rewrite it and throw AlreadyExistException
     */
    void checkAlreadyExist() {
        //do nothing ; if override by a sub-class, should throw AlreadyExist exception
    }

    /**
     * Return domain user (annotation user, image user...)
     * By default, a domain has no user.
     * You need to override userDomainCreator() in domain class
     * @return Domain user
     */
    public SecUser userDomainCreator() {
        return null
    }

    /**
     * Get the container domain for this domain (usefull for security)
     * @return Container of this domain
     */
    public CytomineDomain container() {
        return null
    }

    /**
     * Build callback data for a domain (by default null)
     * Callback are metadata used by client
     * You need to override getCallBack() in domain class
     * @return Callback data
     */
    def getCallBack() {
        return null
    }

    protected static def getAPIBaseFields(def domain) {
        def apiFields = [:]
        apiFields['class'] = domain.class
        apiFields['id'] = domain.id
        apiFields['created'] = domain.created?.time?.toString()
        apiFields['updated'] = domain.updated?.time?.toString()
        apiFields
    }

    protected static def getAPIDomainFields(def domain, LinkedHashMap<String, Object> mapFields = null) {
        def apiFields = [:]

        mapFields?.each {
            String originalFieldName = it.key
            String apiFieldName = it.value["apiFieldName"]
            String accessor = it.value["apiValueAccessor"]
            println "accessor=$accessor"
            if (accessor != "") {
                println "ask accessor $accessor <<<<<<================================================ "
                apiFields["$apiFieldName"] =  domain.class."$accessor"(domain)
            } else {
                println "get field $originalFieldName <<<<<<================================================ "
                apiFields["$apiFieldName"] =  domain."$originalFieldName"
            }
        }

        apiFields
    }

    protected static LinkedHashMap<String, Object> getMappingFromAnnotation(Class clazz) {
        def mapping = [:]

        Field[] fields = clazz.getDeclaredFields()
        // look for fields annotated in super classes
        def cl = clazz
        while (cl.superclass) {
            cl = cl.superclass
            fields += cl.getDeclaredFields()
        }

        for (Field field : fields) {
            if (field.getAnnotation(ApiObjectField.class) != null) {
                ApiObjectField apiObjectField = field.getAnnotation(ApiObjectField.class)
                String apiFieldName = field.getName()
                if (!apiObjectField.apiFieldName().equals(""))
                    apiFieldName = apiObjectField.apiFieldName()
                String apiValueAccessor = apiObjectField.apiValueAccessor()
                /*if (apiValueAccessor.equals("")) {
                    apiValueAccessor = "get" + apiFieldName.capitalize()
                }*/
                mapping.put(field.getName(), [apiFieldName : apiFieldName, apiValueAccessor : apiValueAccessor])
            }
        }
        return mapping
    }


    static def getDataFromDomain(def domain, LinkedHashMap<String, Object> mapFields = null) {
       return getAPIBaseFields(domain) + getAPIDomainFields(domain, mapFields)

    }

    boolean hasPermission(Permission permission) {
        try {
            return hasPermission(this,permission)
        } catch (Exception e) {e.printStackTrace()}
        return false
    }

    boolean checkPermission(Permission permission) {
        boolean right = hasPermission(permission) || cytomineService.currentUser.admin
        return right
    }


    def dataSource
    boolean hasPermission(def domain,Permission permission) {
        def masks = getPermission(domain,cytomineService.getCurrentUser())
        return masks.max() >= permission.mask

        return false
    }


    List getPermission(def domain, def user = null) {
        try {
            String request = "SELECT mask FROM acl_object_identity aoi, acl_sid sid, acl_entry ae " +
            "WHERE aoi.object_id_identity = ${domain.id} " +
                    (user? "AND sid.sid = '${user.humanUsername()}' " : "") +
            "AND ae.acl_object_identity = aoi.id "+
            "AND ae.sid = sid.id "

            def masks = []
            new Sql(dataSource).eachRow(request) {
                masks<<it[0]
            }
            return masks

        } catch (Exception e) {
            println e.toString()
            e.printStackTrace()
        }
        return []
    }


    int compareTo(obj) {
        created.compareTo(obj.created)
    }

    boolean canUpdateContent() {
        //by default, we allow a non-admin user to update domain content
        return true
    }

    String encodeAsJSON() {
        return (this as JSON).toString()
    }

    def get(String id) {
        if(id) {
            return get(Long.parseLong(id))
        } else {
            return null
        }
    }

    def read(String id) {
        if(id) {
            return read(Long.parseLong(id))
        } else {
            return null
        }
    }

}
