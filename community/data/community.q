# Queries for retrieving assemblyID and updating community tables
database=${org.cougaar.configuration.database}
username=${org.cougaar.configuration.user}
password=${org.cougaar.configuration.password}

queryExperimentID = \
 SELECT EXPT_ID \
 FROM v4_expt_experiment \
 WHERE NAME = ':experimentName'

queryAssemblyID = \
 SELECT ASSEMBLY_ID \
 FROM v4_expt_trial_assembly \
 WHERE EXPT_ID = ':exptID'

queryCommunityInfo = \
  SELECT ASSEMBLY_ID \
  FROM community_attribute \
  WHERE COMMUNITY_ID = ':community_id' \
        AND ATTRIBUTE_ID = 'CommunityType' \
	AND ATTRIBUTE_VALUE = ':community_type'

queryEntityInfo = \
  SELECT ASSEMBLY_ID \
  FROM community_entity_attribute \
  WHERE COMMUNITY_ID = ':community_id' \
        AND ENTITY_ID = ':entity_id' \
	AND ATTRIBUTE_ID = ':attribute_id' \
	AND ATTRIBUTE_VALUE = ':attribute_value'

