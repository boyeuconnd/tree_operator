package repo;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import vn.vimo.dao.VimoDaoVersion;
import vn.vimo.dao.entity.Customer;
import vn.vimo.entity.BaseEntity;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "COLLAB_ANCESTOR_PATH")
@Inheritance(strategy = InheritanceType.JOINED)
public class CollabAncestorPath extends BaseEntity {

    private static final long serialVersionUID = VimoDaoVersion.SERIAL_VERSION_UID;

    @Id
    @SequenceGenerator(name = "seqCollabAncestor", sequenceName = "COLLAB_ANCESTOR_PATH_ID", allocationSize = 1)
    @GeneratedValue(generator = "seqCollabAncestor")
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, targetEntity = Customer.class)
    @JoinColumn(name = "CUST_ID", updatable = false, insertable = false)
    private Customer custId;

    @Column(name = "CUST_ID")
    private Long custIdCol;

    @Column(name = "ANCESTOR_PATH")
    private String ancestorPath;

    @Column(name = "HAS_CHILDREN", nullable = false, columnDefinition = "DEFAULT 0")
    @Type(type = "org.hibernate.type.NumericBooleanType")
    private boolean hasChildren;

    @Column(name = "TREE_LEVEL", nullable = false, columnDefinition = "DEFAULT 0")
    private Integer treeLevel;


}
