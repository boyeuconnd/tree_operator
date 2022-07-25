package tree;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import repo.CollabAncestorPath;
import vn.vimo.exception.ErrorCode;
import vn.vimo.exception.Exception;
import vn.vimo.service.config.VimoConfigurationService;
import vn.vimo.user.web.collaborator.CollabConstant;
import vn.vimo.user.web.collaborator.repositories.CollabAncestorPathRepoCustomImpl;
import vn.vimo.user.web.collaborator.repositories.CollabAncestorPathRepository;
import vn.vimo.user.web.collaborator.services.CollabNodeLockService;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
@Slf4j
public class BaseTreeOperator implements TreeStructureOperator {
    private final String groupCode = "vn.vimo.user.web.collaborator.tree";
    private final String key = "ROOT_ID";
    private final Integer ROOT_LEVEL = 0;

    private VimoConfigurationService configurationService;
    private CollabAncestorPathRepository repository;
    private CollabAncestorPathRepoCustomImpl repoCustom;
    private CollabNodeLockService lockService;


    /**
     * Add ChildNodeId to FatherNodeId
     * Throw Exception if FatherNode does not exist
     */
    @Override
//    @Transactional
    public CollabAncestorPath addLeaf(Long fatherNodeId, Long childNodeId) throws Exception {

        CollabAncestorPath fatherNode = this.getNode(fatherNodeId);

        List<Long> parentIds = new ArrayList<>(splitIdsFromPath(fatherNode.getAncestorPath()));
        parentIds.add(fatherNodeId);
        if (lockService.isLocked(parentIds))
            throw new Exception(ErrorCode.NODE_BEING_LOCKED, "One or more nodes being locked by another process");

        if (!fatherNode.isHasChildren()) {
            fatherNode.setHasChildren(true);
            repository.save(fatherNode);
        }

        CollabAncestorPath childNode = new CollabAncestorPath();
        childNode.setHasChildren(false);
        childNode.setCustIdCol(childNodeId);
        childNode.setAncestorPath(
                StringUtils.defaultString(fatherNode.getAncestorPath()) + covertNodeIdToPathElement(fatherNodeId)
        );
        childNode.setTreeLevel(fatherNode.getTreeLevel() + 1);

        return repository.save(childNode);
    }

    /**
     * result = addLeaf(fatherNodeId,childNodeId)
     * if !result
     * if fatherNodeId not existed: find default root and assign to fatherNodeId
     * result = addLeaf(fatherNodeId,childNodeId)
     * return result
     **/
    public CollabAncestorPath addLeafToAnOptionalNode(Long fatherNodeId, Long childNodeId) throws Exception {
        CollabAncestorPath node;
        try {
            node = this.addLeaf(fatherNodeId, childNodeId);
        } catch (Exception vmex) {
            if (vmex.getError().equals(ErrorCode.NODE_NOT_EXIST)) {
                CollabAncestorPath newFatherNode = addLeaf(getRootId(), fatherNodeId);
                node = addLeaf(newFatherNode.getCustIdCol(), childNodeId);
            } else
                throw vmex;
        }
        return node;
    }

    @Override
    public CollabAncestorPath getNode(Long nodeId) throws Exception {
        Optional<CollabAncestorPath> optPath = repository.getByCustIdCol(nodeId);
        return optPath.orElseThrow(() -> new Exception(ErrorCode.NODE_NOT_EXIST, "Node does not exist"));

    }

    @Override
    @Transactional
    public Boolean cutLeaf(Long childNodeId) throws Exception {
        CollabAncestorPath childNode = this.getNode(childNodeId);

        List<Long> checkIds = splitIdsFromPath(childNode.getAncestorPath());
        if (lockService.isLocked(checkIds))
            throw new Exception(ErrorCode.NODE_BEING_LOCKED, "One or more nodes being locked by another process");

        Integer currentVersion = childNode.getVersion();

        if (childNode.isHasChildren()) {
            throw new Exception(ErrorCode.NODE_INVALID, "Current Node is not a leaf");
        } else {
            Integer rowDeleted = repository.deleteByCustIdColAndVersion(childNodeId, currentVersion);
            return rowDeleted != 0;
        }

    }

    /**
     * 1.1 Check Node being locked
     * 1.2 Check Change Father rule (old node is new node's ancestor)
     * 2. Lock 2 father nodes
     * 3. Get all children nodes
     * 4. Update children nodes
     * 5. Update 2 father nodes
     */
    @Override
    public void changeFather(Long oldFatherId, Long newFatherId, Long reqUserId) throws Exception {
        log.info("Old Node: {}, New Node: {}, Requester: {}",oldFatherId,newFatherId,reqUserId);
        CollabAncestorPath oldFatherNode = this.getNode(oldFatherId);
        CollabAncestorPath newFatherNode = this.getNode(newFatherId);
        Set<Long> checkIds = new HashSet<>();
        checkIds.add(oldFatherId);
        checkIds.add(newFatherId);
        checkIds.addAll(splitIdsFromPath(oldFatherNode.getAncestorPath()));
        checkIds.addAll(splitIdsFromPath(newFatherNode.getAncestorPath()));

        if (lockService.isLocked(Lists.newArrayList(checkIds)))
            throw new Exception(ErrorCode.NODE_BEING_LOCKED, "One or more nodes being locked by another process");


        Boolean isCurrentNodeExistInNewNodeAncestry = this.testChildNode(oldFatherId, newFatherNode);
        if (isCurrentNodeExistInNewNodeAncestry)
            throw new Exception(ErrorCode.RULE_VIOLATE, "Hit the rule change father");

        lockService.lockNodes(Arrays.asList(oldFatherId, newFatherId), reqUserId);

        Integer levelDiff = newFatherNode.getTreeLevel() - oldFatherNode.getTreeLevel();
        String oldPath = oldFatherNode.getAncestorPath() + covertNodeIdToPathElement(oldFatherId);
        String newPath = newFatherNode.getAncestorPath() + covertNodeIdToPathElement(newFatherId);

        List<CollabAncestorPath> childrenNodes = getAllChildren(oldFatherId, -1);
        try {
            updateChildrenNode(childrenNodes, oldPath, newPath, levelDiff);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        } finally {
            lockService.unlockNode(Arrays.asList(oldFatherId, newFatherId));
            updateFatherNode(oldFatherNode, newFatherNode);
        }

    }

    @Override
    public Long getParentId(CollabAncestorPath path) throws Exception {
        String ancestryPath = path.getAncestorPath();
        List<Long> ids = splitIdsFromPath(ancestryPath);
        return ids.get(ids.size() - 1);
    }

    @Override
    public CollabAncestorPath getParent(CollabAncestorPath childNode) throws Exception {
        Long fatherId = this.getParentId(childNode);
        return getNode(fatherId);
    }

    /**
     * subLevel <0 : getAll
     * =0 : return []
     * >0 : number of level under father
     */
    public List<CollabAncestorPath> getAllChildren(Long fatherNodeId, Integer subLevel) throws Exception {
        if (subLevel == 0) return Collections.emptyList();

        CollabAncestorPath fatherNode = this.getNode(fatherNodeId);
        String fatherAncestryPath = fatherNode.getAncestorPath() + covertNodeIdToPathElement(fatherNodeId);
        Integer fatherLevel = fatherNode.getTreeLevel();

        List<CollabAncestorPath> resultList = repoCustom.findByCustIdColAndAncestorPath(
                fatherLevel, subLevel, fatherAncestryPath);

        return resultList;

    }

    /**
     * testNode.Path exist currentNode?
     */
    public Boolean testChildNode(Long currentNodeId, CollabAncestorPath testNode) throws Exception {
        String ancestryPath = testNode.getAncestorPath();
        return splitIdsFromPath(ancestryPath).contains(currentNodeId);
    }

    /**
     * Check field hasChildren of single Node.
     */
    @Transactional
    public CollabAncestorPath updateNode(Long nodeId, Integer nodeVersion) throws Exception {
        if (lockService.isLocked(nodeId))
            throw new Exception(ErrorCode.NODE_BEING_LOCKED, "One or more nodes being locked by another process");

        List<CollabAncestorPath> children = this.getAllChildren(nodeId, 1);

        repository.updateByCustIdColAndVersion(nodeId, nodeVersion, !children.isEmpty());
        return getNode(nodeId);
    }


    @Override
    public boolean isRootNode(Long testId) throws Exception {
        CollabAncestorPath testNode = this.getNode(testId);
        return StringUtils.isEmpty(testNode.getAncestorPath())
                && testNode.getTreeLevel()==0;
    }

    @Override
    public void lockNode(Long customerId,Long reqUserId, OffsetDateTime expiredTime,String desc) {
        lockService.lockNodesWithTime(Collections.singletonList(customerId),reqUserId,expiredTime,desc);
    }

    @Override
    public void unlockNode(Long customerId) {
        lockService.unlockNode(Collections.singletonList(customerId));
    }

    /**
     * PRIVATE METHODS
     */
    private String getParentPath(CollabAncestorPath path) {
        if (path == null) return covertNodeIdToPathElement(getRootId());
        return path.getAncestorPath();
    }


    private Long getRootId() {
        return (Long) configurationService.getAndCacheSystemConfig(groupCode, key);
    }

    private Integer getParentTreeLevel(CollabAncestorPath path) {
        if (path == null) return ROOT_LEVEL;
        return path.getTreeLevel();
    }

    private CollabAncestorPath getAncestryPathByNodeId(Long nodeId) {
        Optional<CollabAncestorPath> optPath = repository.getByCustIdCol(nodeId);
        return optPath.orElse(null);
    }

    private String covertNodeIdToPathElement(Long nodeId) {
        return CollabConstant.PATH_ELEMENT_OPEN + nodeId + CollabConstant.PATH_ELEMENT_CLOSE;
    }

    private static List<Long> splitIdsFromPath(String path) throws Exception {
        if(path == null) return Collections.emptyList();

        if (!Pattern.compile("^(\\{\\w+}){1,}$").matcher(path).matches())
            throw new Exception(ErrorCode.INVALID_PARMETER, "Ancestry Path Invalid Format");
        String[] idsArr = path.replaceAll("\\{", "").split("}");
        List<String> idsList = Arrays.asList(idsArr);
        return idsList.stream().map(Long::parseLong).collect(Collectors.toList());
    }

    @Transactional
    void updateChildrenNode(List<CollabAncestorPath> childrenNodes, String oldPath, String newPath, Integer levelDiff) {
        childrenNodes.forEach(node -> {
            String newAncestorPath = node.getAncestorPath().replace(oldPath, newPath);
            node.setAncestorPath(newAncestorPath);
            node.setTreeLevel(node.getTreeLevel() + levelDiff);
        });
        repository.save(childrenNodes);
    }

    @Transactional
    void updateFatherNode(CollabAncestorPath oldFatherNode, CollabAncestorPath newFatherNode) {
        oldFatherNode.setHasChildren(false);
        if (newFatherNode.isHasChildren()) {
            repository.save(oldFatherNode);
        } else {
            newFatherNode.setHasChildren(true);
            repository.save(Arrays.asList(oldFatherNode, newFatherNode));
        }

    }

}
