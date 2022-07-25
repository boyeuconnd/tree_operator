package repo;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.NaturalId;
import vn.vimo.dao.VimoDaoVersion;
import vn.vimo.dao.enumuration.EnumCustomerStatus;
import vn.vimo.dao.enumuration.EnumCustomerType;

import javax.persistence.*;
import java.util.HashSet;


@Entity
@Table(name = "CUSTOMERS")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
public abstract class Customer extends BaseEntity {

	private static final long serialVersionUID = VimoDaoVersion.SERIAL_VERSION_UID;

	@Id
	@GeneratedValue(generator = "seqCustomer")
	@SequenceGenerator(name = "seqCustomer", sequenceName = "SEQ_CUSTOMER", allocationSize = 1)
	private Long id;

	@NaturalId
	private String code;

	private String email;

	private String phoneNumber;

	@Enumerated(EnumType.STRING)
	private EnumCustomerType type;

	@Enumerated(EnumType.STRING)
	private EnumCustomerStatus status;

	private Long parentId;

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "customer")
	private Set<CusVerify> cusVerifies = new HashSet<>(0);

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "customer")
	private Set<Account> accounts = new HashSet<>();

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "customer")
	private Set<User> users = new HashSet<>();

	private String avatar;

	private String fullname;

	private String note;

	@OneToMany(mappedBy = "customer")
	@JsonManagedReference
	private List<ServiceSubscription> serviceSubscriptions;

}
