<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns:xs="http://www.w3.org/2001/XMLSchema" 
	xmlns:fn="http://www.w3.org/2005/xpath-functions"
	xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
	xmlns:cim="http://iec.ch/TC57/2013/CIM-schema-cim16#"
	xmlns:md="http://iec.ch/TC57/61970-552/ModelDescription/1#"
	xmlns:entsoe="http://entsoe.eu/CIM/SchemaExtension/3/1#"
	xmlns:pti="http://www.pti-us.com/PTI_CIM-schema-cim16#"
	xmlns:fgh="http://fgh-ma.de/Integral/ProfileExtension/1#"
	xmlns:cc="http://schneider-electric-dms.com/CimConverter"
	xmlns:brlnd="http://brolunda.com/ecore-converter#"
	xmlns:neplan="http://www.neplan.ch#">
	<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes"/>

	<!--
	This script was created 2019-03-18 by Lars-Ola Österlund/Brolunda Consulting.

	The script do two things
	- filter out data not in the profiles. EMF can only load data that is
	present in the information model, hence this filter remove data so the rest 
	match up with the profiles. It also generates a document with cim data not in the profile.
	- add the custom attribute brlnd:ModelObject.xxModel references to the FullModel
	
	The script is controlled by a control file; cim16_analyse_igm.xml or cim16_analyse_igm_cxtp.xml
	The control files describe the inputs to script as well as where the result is saved.

	Change history
	2019-03-18 New script
	2019-06-14 LOO Updated to add object references to FullModel. DifferenceModel not supported
	-->
	
	<!--<xsl:template match="/rdf:RDF" priority="2">
		<xsl:apply-templates select="BATCH"/>
	</xsl:template>
	
	<xsl:template match="BATCH" priority="2">
		<xsl:apply-templates select="cim16_clean_data_not_in_profile">
			<xsl:with-param name="batch" select="."/>
		</xsl:apply-templates>
	</xsl:template>
	
	<xsl:template match="cim16_clean_data_not_in_profile" priority="2">
		<xsl:param name="batch"/>
		<xsl:apply-templates select="FILE_TO_CLEAN">
			<xsl:with-param name="batch" select="$batch"/>
		</xsl:apply-templates>
	</xsl:template>-->

	<xsl:param name="file"/>

	<xsl:template match="/rdf:RDF" priority="2">
		<xsl:apply-templates select="ACTION"/>
	</xsl:template>

	<xsl:template match="ACTION" priority="2">
		<xsl:apply-templates select="CLEAN_PROFILES"/>
	</xsl:template>

	<xsl:template match="CLEAN_PROFILES" priority="2">
		<xsl:apply-templates select="CLEAN_PROFILE"/>

	</xsl:template>

	<xsl:template match="CLEAN_PROFILE" priority="2">

		<xsl:variable name="input_file" select="fn:document($file)"/>





		<xsl:variable name="cleaned_file" >
			<xsl:apply-templates select="$input_file" mode="clean"/>
		</xsl:variable>
		<xsl:copy-of select="$cleaned_file" />
		<!--<xsl:variable name="not_allowed_names">
			<xsl:apply-templates select="$input_file/rdf:RDF/*[fn:name() != 'md:FullModel']" mode="class">
				<xsl:with-param name="report" select=" 'true' "/>
				<xsl:with-param name="model" select="$input_file/rdf:RDF/md:FullModel"/>
				<xsl:with-param name="profile">
					<xsl:call-template name="get_profile">
						<xsl:with-param name="profile" select="$input_file/rdf:RDF/md:FullModel/md:Model.profile[1]"/>
					</xsl:call-template>
				</xsl:with-param>
			</xsl:apply-templates>
		</xsl:variable>

		<xsl:if test="$not_allowed_names/*">
			<xsl:result-document href="{$file_name_not_allowed_cim_names}">
				<xsl:apply-templates select="$input_file" mode="report">
					<xsl:with-param name="not_allowed_names" select="$not_allowed_names/*"/>
				</xsl:apply-templates>
			</xsl:result-document>
		</xsl:if>-->
	</xsl:template>

	<xsl:template name="get_base_directory">
		<xsl:param name="path"/>
		<xsl:choose>
			<xsl:when test="fn:substring-after(fn:substring-after($path, '/'), '/') = '' ">
				<xsl:value-of select="fn:substring-before($path, '/')"/>
			</xsl:when>
			<xsl:otherwise>
				<xsl:value-of select="fn:substring-before($path, '/')"/>/<xsl:call-template name="get_base_directory">
					<xsl:with-param name="path" select="fn:substring-after($path, '/')"/>
				</xsl:call-template>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template match="/rdf:RDF" mode="clean">
		<xsl:copy>
			<xsl:namespace name="brlnd">http://brolunda.com/ecore-converter#</xsl:namespace>
			<xsl:copy-of select="@*"/>
			<xsl:apply-templates mode="class">
				<xsl:with-param name="model" select="md:FullModel"/>
				<xsl:with-param name="profile">
					<xsl:call-template name="get_profile">
						<xsl:with-param name="profile" select="md:FullModel/md:Model.profile[1]"/>
					</xsl:call-template>
				</xsl:with-param>
			</xsl:apply-templates>
		</xsl:copy>
	</xsl:template>
	
	<xsl:template name="get_profile">
		<xsl:param name="profile"/>
		<xsl:choose>
			<xsl:when test="fn:contains($profile, 'entsoe.eu/CIM/Equipment')">Eq</xsl:when>
			<xsl:when test="fn:contains($profile, 'entsoe.eu/CIM/Topology')">Tp</xsl:when>
			<xsl:when test="fn:contains($profile, 'entsoe.eu/CIM/SteadyStateHypothesis')">Ssh</xsl:when>
			<xsl:when test="fn:contains($profile, 'entsoe.eu/CIM/StateVariables')">Sv</xsl:when>
			<xsl:otherwise>ERROR: No profile found</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template match="/rdf:RDF" mode="report">
		<xsl:param name="not_allowed_names"/>
		<xsl:copy>
			<xsl:copy-of select="@*"/>
			<xsl:copy-of select="$not_allowed_names"/>
		</xsl:copy>
	</xsl:template>
	
	<xsl:template match="cim:BasePower
						| cim:ReportingGroup
						| cim:OperatingParticipant
						| cim:OperatingShare
						| cc:ResourceOwner
						| cc:ResourceShare
						| cim:BranchGroupTerminal
						| cim:BranchGroup
						| cim:BranchLimitPriority
						| cim:PSRType
						" priority="2" mode="class">
		<xsl:param name="report"/>
		<xsl:param name="model"/>
		<xsl:param name="profile"/>
		<xsl:if test="$report='true' and fn:contains(fn:name(), 'cim:')">
			<xsl:copy>
				<xsl:copy-of select="@*"/>
			</xsl:copy>
		</xsl:if>			
	</xsl:template>
						
	<xsl:template match="cim:SvPowerFlow | cim:SvVoltage | cim:SvShuntCompensatorSections | cim:SvTapStep | cim:SvInjection | cim:SvStatus" priority="2" mode="class">
		<xsl:param name="report"/>
		<xsl:param name="model"/>
		<xsl:param name="profile"/>
		<xsl:if test="not($report) and $profile = 'Sv'">
			<xsl:copy>
				<xsl:copy-of select="@*"/>
				<xsl:copy-of select="*[name() != 'cim:IdentifiedObject.name']"/>
				<xsl:element name="brlnd:ModelObject.SvModel">
					<xsl:attribute name="rdf:resource" select="$model/@rdf:about"/>
				</xsl:element>
			</xsl:copy>
		</xsl:if>
		<xsl:if test="not($report) and $profile != 'Sv'">ERROR: Profile missing</xsl:if>
	</xsl:template>
	
	<xsl:template match="cim:IdentifiedObject.aliasName
						| cim:Equipment.normallyInService 
						| cim:SynchronousMachine.maxU
						| cim:SynchronousMachine.minU
						| cim:SynchronousMachine.baseQ
						| cim:PowerSystemResource.ReportingGroup
						| cim:GeneratingUnit.baseP
						| cim:GeneratingUnit.highControlLimit
						| cim:GeneratingUnit.lowControlLimit
						| cim:GeneratingUnit.maxEconomicP
						| cim:GeneratingUnit.minEconomicP
						| cim:Bay.Substation
						| cim:IdentifiedObject.pathName
						| cim:TapChanger.initialDelay
						| cim:GeneratingUnit.genControlMode
						| cim:PowerTransformer.vectorGroup
						| pti:ThermalGeneratingUnit.subtype
						| pti:SeriesCompensator.sctyp
						| pti:OperationalLimitSet.Season
						| pti:MeasurementValueSource.PSSOInclude
						| pti:MeasurementValueSource.PSSOStatus0
						| pti:MeasurementValueSource.PSSOStatus1
						| pti:MeasurementValueSource.PSSOStatus2
						| pti:MeasurementValueSource.PSSOStatus3
						| pti:ACLineSegment.sequenceNumber
						| pti:ACLineSegment.MET
						| pti:Resources.PSSEID
						| pti:Resources.PSSEStatus
						| pti:EnergyConsumer.interruptibleStatus
						| pti:IdentifiedObject.assetID
						| pti:Measurement.PSSOStandardDeviation
						| pti:Measurement.PSSOControlCode
						| pti:Measurement.PSSOTelemetryCode
						| pti:Measurement.PSSOLimit
						| pti:Measurement.PSSOMeasuredValue
						| pti:Equipment.excludeFromCase
						| pti:BaseVoltage.PTIColor
						| pti:GeneratingUnit.rmpct
						| pti:GeneratingUnit.rt
						| pti:GeneratingUnit.xt
						| pti:GeneratingUnit.gtap
						| pti:ShuntCompensator.shuntType
						| pti:ShuntCompensator.controlMode
						| pti:ShuntCompensator.adjMethod
						| pti:PowerTransformer.cw
						| pti:PowerTransformer.cz
						| pti:PowerTransformer.cm
						| pti:PowerTransformer.cr
						| pti:PowerTransformer.cx
						| pti:PowerTransformer.rma
						| pti:PowerTransformer.rmi
						| pti:PowerTransformer.nmetr
						| pti:PowerTransformerEnd.ang
						| pti:PowerTransformerEnd.nomV
						| pti:PowerTransformerEnd.sBase
						| pti:EnergyConsumer.slackLoad
						| pti:SynchronousMachine.x
						| pti:TopologicalNode.IDE
						| pti:Model.createdBy
						| pti:TopologicalNode.OperatingParticipant
						| fgh:IdentifiedObject.ukz
						| fgh:IdentifiedObject.langname
						| fgh:ACLineSegment.stkabname
						| fgh:SynchronousMachine.generatorTyp
						| fgh:Model.creator
						| cc:TopologicalNode.ResourceOwner
						| neplan:Model.createdBy
						| cim:Terminal.BranchGroupTerminal
						| md:Model.SuperSedes
						" priority="2" mode="attribute">
			<xsl:param name="report"/>
			<xsl:if test="$report='true' and fn:contains(fn:name(), 'cim:')">
				<xsl:copy>
					<xsl:attribute name="rdf:ID" select="../@rdf:ID | ../@rdf:about"/>
				</xsl:copy>
			</xsl:if>		
		</xsl:template>
	
	<xsl:template match="*" priority="1" mode="class">
		<xsl:param name="report"/>
		<xsl:param name="model"/>
		<xsl:param name="profile"/>
		<xsl:if test="fn:not($report)">
			<xsl:copy>
				<xsl:copy-of select="@*"/>
				<xsl:apply-templates mode="attribute"/>
				<xsl:if test="$profile = 'Eq' and fn:name() != 'md:FullModel'">
					<xsl:element name="brlnd:ModelObject.EqModel">
						<xsl:attribute name="rdf:resource" select="$model/@rdf:about"/>
					</xsl:element>
				</xsl:if>
				<xsl:if test="$profile = 'Tp' and fn:name() != 'md:FullModel'">
					<xsl:element name="brlnd:ModelObject.TpModel">
						<xsl:attribute name="rdf:resource" select="$model/@rdf:about"/>
					</xsl:element>
				</xsl:if>
				<xsl:if test="$profile = 'Ssh' and fn:name() != 'md:FullModel'">
					<xsl:element name="brlnd:ModelObject.SshModel">
						<xsl:attribute name="rdf:resource" select="$model/@rdf:about"/>
					</xsl:element>
				</xsl:if>
				<xsl:if test="$profile = 'Sv' and fn:name() != 'md:FullModel'">
					<xsl:element name="brlnd:ModelObject.SvModel">
						<xsl:attribute name="rdf:resource" select="$model/@rdf:about"/>
					</xsl:element>
				</xsl:if>
			</xsl:copy>
		</xsl:if>
		<xsl:if test="$report='true'">
			<xsl:apply-templates mode="attribute">
				<xsl:with-param name="report" select="$report"/>
			</xsl:apply-templates>
		</xsl:if>
	</xsl:template>
	
	<xsl:template match="cim:Season.endDate | cim:Season.startDate" priority="2" mode="attribute">
		<xsl:param name="report"/>
		<xsl:if test="fn:not($report)">
			<xsl:copy>
				<xsl:choose>
					<xsl:when test="fn:contains(., '--')">2019<xsl:value-of select="fn:substring-after(., '-')"/></xsl:when>
					<xsl:otherwise><xsl:value-of select="."/></xsl:otherwise>
				</xsl:choose>
			</xsl:copy>
		</xsl:if>
		<xsl:if test="$report = 'true' and fn:contains(., '--') ">
			<xsl:comment>Bad date</xsl:comment>
			<xsl:copy-of select="."/>
		</xsl:if>
	</xsl:template>
	
	<xsl:template match="*" priority="1" mode="attribute">
		<xsl:param name="report"/>
		<xsl:if test="fn:not($report)">
			<xsl:copy>
				<xsl:copy-of select="@*"/>
				<xsl:choose>
					<xsl:when test=". = 'Inf'">1000</xsl:when>
					<xsl:otherwise><xsl:value-of select="."/></xsl:otherwise>
				</xsl:choose>
			</xsl:copy>
		</xsl:if>
		<xsl:if test="$report = 'true' and . = 'Inf' ">
			<xsl:comment>Code not allowed</xsl:comment>
			<xsl:copy-of select="."/>
		</xsl:if>
	</xsl:template>
	
</xsl:stylesheet>
