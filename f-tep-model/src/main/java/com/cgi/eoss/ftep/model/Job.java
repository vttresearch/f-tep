package com.cgi.eoss.ftep.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Multimap;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Type;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.Lob;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * <p>Representation of a single {@link JobConfig} execution.</p>
 */
@Data
@ToString(exclude = {"parentJob"})
@EqualsAndHashCode(exclude = {"id", "subJobs"})
@Table(name = "ftep_jobs",
        indexes = {
                @Index(name = "ftep_jobs_job_config_idx", columnList = "job_config"),
                @Index(name = "ftep_jobs_owner_idx", columnList = "owner")
        },
        uniqueConstraints = {@UniqueConstraint(columnNames = "ext_id")})
@NoArgsConstructor
@Entity
public class Job implements FtepEntityWithOwner<Job>, FtepFileReferencer {

    /**
     * <p>Internal unique identifier of the job.</p>
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * <p>The external unique job identifier. Must be provided (e.g. UUID from the WPS server).</p>
     */
    @Column(name = "ext_id", nullable = false, updatable = false)
    private String extId;

    /**
     * <p>The job configuration used to launch this specific execution.</p>
     */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "job_config", nullable = false)
    private JobConfig config;

    /**
     * <p>The user executing this job.</p>
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner", nullable = false)
    private User owner;

    /**
     * <p>The date and time this job was launched.</p>
     */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /**
     * <p>The date and time this job execution ended.</p>
     */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /**
     * <p>The current execution status of the job.</p>
     */
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private Status status = Status.CREATED;

    /**
     * <p>The id of the worker executing the job.</p>
     */
    @Column(name = "worker_id")
    private String workerId;

    /**
     * <p>Current stage of execution. Maybe arbitrarily set by service implementations to inform the user of
     * progress.</p>
     */
    @Column(name = "stage")
    private String stage;

    /**
     * <p>URL to the graphical interface if this is a {@link FtepService.Type#APPLICATION}.</p>
     */
    @Column(name = "gui_url")
    private String guiUrl;

    /**
     * <p>Backend endpoint for GUI if this is a {@link FtepService.Type#APPLICATION}.</p>
     */
    @Column(name = "gui_endpoint")
    private String guiEndpoint;

    /**
     * <p>The job execution outputs.</p>
     */
    @Lob
    @Type(type = "com.cgi.eoss.ftep.model.converters.StringMultimapYamlConverter")
    @Column(name = "outputs")
    private Multimap<String, String> outputs;

    /**
     * <p>The FtepFiles produced as job outputs.</p>
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ftep_job_output_files",
            joinColumns = @JoinColumn(name = "job_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "file_id", nullable = false),
            indexes = @Index(name = "ftep_job_output_files_job_file_idx", columnList = "job_id, file_id", unique = true)
    )
    private Set<FtepFile> outputFiles = new HashSet<>();

    /**
     * <p>The subjobs produced from a job related to a parallel processor</p>
     */
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, mappedBy = "parentJob")
    @OrderBy("id asc")
    @JsonIgnore
    private Set<Job> subJobs = new HashSet<>();

    /**
     * <p>The parent job of a subjob</p>
     */
    @ManyToOne
    private Job parentJob;

    /**
     * <p>Tells if a job is a parent job</p>
     */
    @Column(name = "is_parent")
    private boolean parent = false;

    public Job(JobConfig config, String extId, User owner, Job parentJob) {
        this(config, extId, owner);
        this.parentJob = parentJob;
    }

    public Job(JobConfig config, String extId, User owner) {
        this.config = config;
        this.extId = extId;
        this.owner = owner;
    }

    public void addSubJob(Job job) {
        subJobs.add(job);
    }

    public void addOutputFile(FtepFile outputFile) {
        outputFiles.add(outputFile);
    }

    @Override
    public int compareTo(Job o) {
        return ComparisonChain.start().compare(startTime, o.startTime).result();
    }

    @Override
    public Boolean removeReferenceToFtepFile(FtepFile file) {
        return this.outputFiles.remove(file);
    }


    public enum Status {
        CREATED, RUNNING, COMPLETED, ERROR, CANCELLED
    }

    /**
     * Calculate difference in days between two job input parameters.
     * The parameters have to be in format yyyy-mm-dd.
     * 
     * @param param1
     * @param param2
     * @return 
     * @throws CostingExpressionException if evaluation fails
     */
    public int dayDifference(String param1, String param2) throws CostingExpressionException {
        String s1 = null;
        Multimap<String, String> inputs = this.config.getInputs();
        if (inputs.containsKey(param1)) {
            java.util.Collection<String> value = inputs.get(param1);
            if (value != null && value.size() == 1) {
                s1 = (String) value.toArray()[0];
            }
        }
        String s2 = null;
        if (inputs.containsKey(param2)) {
            java.util.Collection<String> value = inputs.get(param2);
            if (value != null && value.size() == 1) {
                s2 = (String) value.toArray()[0];
            }
        }
        if (s1 != null && s2 != null) {
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            try {
                java.time.LocalDateTime d1 = java.time.LocalDateTime.parse(s1 + " 00:00:00", dtf);
                java.time.LocalDateTime d2 = java.time.LocalDateTime.parse(s2 + " 00:00:00", dtf);
                if (d1.isBefore(d2)) {
                    return (int)java.time.Duration.between(d1, d2).toDays();
                } else {
                    return (int)java.time.Duration.between(d2, d1).toDays();
                }
            } catch (Exception e) {
                throw new CostingExpressionException("Cost expression evaluation failed", e);
            }
        }
        throw new CostingExpressionException("Cost expression evaluation failed");
    }
    /**
     * Calculate difference in days between two job input parameters.
     * The parameters have to be in format yyyy-mm-dd.
     *
     * @param param1
     * @param param2
     * @param scale Scaling factor to be applied to the day difference
     * @param minimum The minimum value to return regardless of other inputs
     * @return
     * @throws CostingExpressionException if evaluation fails
     */
    public int scaledDayDifference(String param1, String param2, double scale, int minimum) throws CostingExpressionException {
        int days = dayDifference(param1, param2);
        int scaled = (int)Math.ceil(days * scale);
        return Math.max(scaled, minimum);
    }
}
