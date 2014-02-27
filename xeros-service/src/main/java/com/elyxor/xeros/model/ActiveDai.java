package com.elyxor.xeros.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "xeros_active_dai")
public class ActiveDai {

	public ActiveDai() {}
	
	private int id;
	private Integer drySmart;
	private Machine machine;
	private String daiIdentifier;
	
    @Id
    @Column(name = "active_dai_id", columnDefinition = "INT unsigned")
    @GeneratedValue(strategy=GenerationType.AUTO)    
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}
	
	@Column(name = "dry_smart", columnDefinition = "INT unsigned")
	public Integer getDrySmart() {
		return drySmart;
	}

	public void setDrySmart(Integer drySmart) {
		this.drySmart = drySmart;
	}

	@ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_profile_id", referencedColumnName = "machine_id")
	public Machine getMachine() {
		return machine;
	}

	public void setMachine(Machine machine) {
		this.machine = machine;
	}

	@Column(name="dai_identifier")
	public String getDaiIdentifier() {
		return daiIdentifier;
	}

	public void setDaiIdentifier(String daiIdentifier) {
		this.daiIdentifier = daiIdentifier;
	}

			
}