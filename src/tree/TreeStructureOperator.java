package tree;

import repo.CollabAncestorPath;
import vn.vimo.exception.VimoException;

import java.time.OffsetDateTime;
import java.util.List;

public interface TreeStructureOperator {
    CollabAncestorPath addLeaf(Long fatherNodeId, Long childNodeId) throws Exception;

    Boolean cutLeaf(Long childNodeId) throws Exception;

    Boolean testChildNode(Long currentNodeId,CollabAncestorPath testNode) throws Exception;

    CollabAncestorPath updateNode(Long currentNodeId, Integer nodeVersion) throws Exception;

    List<CollabAncestorPath> getAllChildren(Long fatherCustId, Integer subLevel) throws Exception;

    void changeFather(Long currentFatherCustId,Long newFatherCustId, Long reqUserId) throws Exception;

    Long getParentId(CollabAncestorPath childNode) throws Exception;

    CollabAncestorPath getParent(CollabAncestorPath childNode) throws Exception;

    CollabAncestorPath getNode(Long nodeId)throws Exception;

    boolean isRootNode(Long testId) throws Exception;

    void lockNode(Long customerId,Long reqUserId, OffsetDateTime expiredTime,String desc);

    void unlockNode(Long customerId);
}
