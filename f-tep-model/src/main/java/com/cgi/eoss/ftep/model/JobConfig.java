package com.cgi.eoss.ftep.model;

import com.cgi.eoss.ftep.model.converters.StringListConverter;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.querydsl.core.annotations.PropertyType;
import com.querydsl.core.annotations.QueryType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>Configuration representing the inputs of a specific service execution.</p>
 * <p>This object is suitable for sharing for re-execution, independent of the actual run status and outputs.</p>
 */
@Data
@EqualsAndHashCode(exclude = {"id", "parent", "inputFiles"})
@ToString(exclude = {"inputs", "parent", "inputFiles"})
@Table(name = "ftep_job_configs",
        indexes = {
                @Index(name = "ftep_job_configs_service_idx", columnList = "service"),
                @Index(name = "ftep_job_configs_owner_idx", columnList = "owner"),
                @Index(name = "ftep_job_configs_label_idx", columnList = "label")
        },
        uniqueConstraints = @UniqueConstraint(name = "ftep_job_configs_unique_idx", columnNames = {"owner", "service", "inputs", "parent", "systematic_parameter", "parallel_parameters", "search_parameters"}))
@NoArgsConstructor
@Entity
public class JobConfig implements FtepEntityWithOwner<JobConfig>, FtepFileReferencer {

    /**
     * <p>Unique identifier of the job.</p>
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * <p>The user owning the job configuration.</p>
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner", nullable = false)
    private User owner;

    /**
     * <p>Parent job to attach to, if any.</p>
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent")
    private Job parent;

    /**
     * <p>The service this job is configuring.</p>
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service")
    private FtepService service;

    /**
     * <p>The job input parameters.</p>
     */
    @Lob
    @Type(type = "com.cgi.eoss.ftep.model.converters.StringMultimapYamlConverter")
    @Column(name = "inputs")
    private Multimap<String, String> inputs = HashMultimap.create();

    /**
     * <p>Human-readable label to tag and identify jobs launched with this configuration.</p>
     */
    @Column(name = "label")
    private String label;

    /**
     * <p>Tag and identify parameter that will be dynamically getting values.</p>
     */
    @Column(name = "systematic_parameter")
    private String systematicParameter;

    @Column(name = "parallel_parameters", nullable = false)
    @Convert(converter = StringListConverter.class)
    @QueryType(PropertyType.SIMPLE)
    private List<String> parallelParameters = new ArrayList<>();

    @Column(name = "search_parameters",  nullable = false)
    @Convert(converter = StringListConverter.class)
    @QueryType(PropertyType.SIMPLE)
    private List<String> searchParameters = new ArrayList<>();

    /**
     * <p>The FtepFiles required as job inputs.</p>
     */
    @OneToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ftep_job_config_input_files",
            joinColumns = @JoinColumn(name = "job_config_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "file_id", nullable = false),
            indexes = @Index(name = "ftep_job_config_input_files_job_config_file_idx", columnList = "job_config_id, file_id", unique = true)
    )
    private Set<FtepFile> inputFiles = new HashSet<>();

    /**
     * <p>Create a new JobConfig instance with the minimum required parameters.</p>
     *
     * @param owner   The user who owns the job
     * @param service The service this job is running on
     */
    public JobConfig(User owner, FtepService service) {
        this.owner = owner;
        this.service = service;
    }

    public void addInputFile(FtepFile inputFile) {
        inputFiles.add(inputFile);
    }

    @Override
    public int compareTo(JobConfig o) {
        return ComparisonChain.start().compare(service, o.service).result();
    }


    @Override
    public Boolean removeReferenceToFtepFile(FtepFile file) {
        return this.inputFiles.remove(file);
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
        if (this.inputs.containsKey(param1)) {
            java.util.Collection<String> value = this.inputs.get(param1);
            if (value != null && value.size() == 1) {
                s1 = (String) value.toArray()[0];
            }
        }
        String s2 = null;
        if (this.inputs.containsKey(param2)) {
            java.util.Collection<String> value = this.inputs.get(param2);
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


    /**
     * Calculate difference in days between two job input parameters.
     * The parameters have to be in format yyyy-mm-dd.
     *
     * @param param1
     * @param param2
     * @param scale Scaling factor to be applied to the day difference
     * @param minimum The minimum value to return regardless of other inputs
     * @param maxParam Name of the parameter that contains an integer value that is the maximum value to return
     * @param noRunParam If the parameter has value True cost estimate is 0
     * @return
     * @throws CostingExpressionException if evaluation fails
     */
    public int sen2LikeSingleTileEstimate(String param1, String param2, 
            double scale, int minimum, String maxParam, String noRunParam) throws CostingExpressionException {
        int days = dayDifference(param1, param2);
        int scaled = (int)Math.ceil(days * scale);
        if (scaled < minimum) {
            scaled = minimum;
        }
        if (maxParam != null) {
            String p = null;
            if (this.inputs.containsKey(maxParam)) {
                java.util.Collection<String> value = this.inputs.get(maxParam);
                if (value != null && value.size() == 1) {
                    p = (String) value.toArray()[0];
                }
            }
            if (p != null  && !p.trim().equals("")) {
                try {
                    int maximum = Integer.valueOf(p);
                    if (maximum > 0 && scaled > maximum) {
                        scaled = maximum;
                    }
                } catch (Exception e) {
                    throw new CostingExpressionException("Failed to parse value for maxParam");
                }
            }
        }
        // No cost in no-run mode
        if (noRunParam != null) {
            String p = null;
            if (this.inputs.containsKey(noRunParam)) {
                java.util.Collection<String> value = this.inputs.get(noRunParam);
                if (value != null && value.size() == 1) {
                    p = (String) value.toArray()[0];
                    boolean noRun = Boolean.valueOf(p);
                    if (noRun) {
                        scaled = 0;
                    }
                }
            }
        }
        return scaled;
    }
}
