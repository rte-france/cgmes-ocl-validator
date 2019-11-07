<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
				xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
				xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
				xmlns:cim="http://iec.ch/TC57/2013/CIM-schema-cim16#"
				xmlns:md="http://iec.ch/TC57/61970-552/ModelDescription/1#"
				xmlns:entsoe="http://entsoe.eu/CIM/SchemaExtension/3/1#"
				xmlns:brlnd="http://brolunda.com/ecore-converter#"
				xmlns:pti="http://www.pti-us.com/PTI_CIM-schema-cim16#"
				xmlns:fn="http://www.w3.org/2005/xpath-functions">
	<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes"/>
	<!--
	This script was created 2018-11-11 by Lars-Ola Österlund/Brolunda Consulting.

	The script merge an EQ document with selected other documents to a model document.
	Typcally a merged IGM with EQ, SSH, TP and SV is created.
	EQBD and TPBD are included with EQ and TP.
	The script is controlled by a control file; cim16_analyse_igm.xml or cim16_analyse_igm_cxtp.xml
	The control files describe the inputs to script as well as where the result is saved.

	Change history
	2018-11-11 LOO New file
	2018-11-21 LOO Updated to be EQ only
	2019-02-24 LOO Merge changed to be configurable.
	2019-04-04 LOO Bug on CsConverter from SV corrected
	2019-04-09 LOO Bug on boundary TNs missing contianer in BB-models corrected
	2019-06-14 LOO FullModel elements are kept as is
	2019-06-28 LOO Incomplete objects included in full model
	2019-08-01 LOO FullModel extended with all meta data
	-->

	<xsl:key name="resource" match="/rdf:RDF/*/*" use="@rdf:resource"/>
	<xsl:key name="cn_resource" match="/rdf:RDF/cim:ConnectivityNode" use="cim:ConnectivityNode.TopologicalNode/@rdf:resource"/>
	<xsl:key name="cn_terminal" match="/rdf:RDF/cim:Terminal" use="cim:Terminal.ConnectivityNode/@rdf:resource"/>
	<xsl:key name="tn_terminal" match="/rdf:RDF/cim:Terminal" use="cim:Terminal.TopologicalNode/@rdf:resource"/>
	<xsl:key name="about" match="/rdf:RDF/*" use="@rdf:about"/>
	<xsl:key name="id" match="/rdf:RDF/*" use="@rdf:ID"/>

	
	<xsl:param name="EQBD" />
	<xsl:param name="TPBD" />
	<xsl:param name="EQ" />
	<xsl:param name="TP" />
	<xsl:param name="SSH" />
	<xsl:param name="SV" />
	<xsl:param name="eq_sn"/>
	<xsl:param name="tp_sn"/>
	<xsl:param name="ssh_sn"/>
	<xsl:param name="sv_sn"/>
	<xsl:param name="eqbd_sn"/>
	<xsl:param name="tpbd_sn"/>
	<!--<xsl:template match="/rdf:RDF" priority="2">
		<xsl:apply-templates select="BATCH"/>
	</xsl:template>

	<xsl:template match="BATCH" priority="2">
		<xsl:apply-templates select="cim16_merge_igm">
			<xsl:with-param name="batch" select="."/>
		</xsl:apply-templates>
	</xsl:template>-->
	<xsl:template match="/rdf:RDF" priority="2">
		<xsl:apply-templates select="ACTION"/>
	</xsl:template>

	<xsl:template match="ACTION" priority="2">
		<xsl:apply-templates select="MERGE"/>
	</xsl:template>

	<xsl:template match="MERGE" priority="2">
		<xsl:apply-templates select="MERGE_PROFILES"/>
	</xsl:template>

	<xsl:template match="MERGE_PROFILES" priority="2">
		<!--<xsl:param name="batch"/>
		<xsl:variable name="current" select="."/>
		<xsl:variable name="file_name" select="$batch/*[name() = $current/@file_name_from]"/>

		<xsl:variable name="result_file_name" select="fn:concat('BATCH', $batch/@nr, '/', fn:substring-before($file_name, '.xml'), '_', @suffix, '.xml')"/>
-->

		<xsl:variable name="eqbd" select="fn:document($EQBD)"/>

		<xsl:variable name="tpbd" select="fn:document($TPBD)"/>



		<xsl:variable name="eq" select="fn:parse-xml($EQ)" />
		<xsl:variable name="tp" select="fn:parse-xml($TP)"/>


		<!-- EQ and TP merged -->
		<xsl:variable name="eq_tp">
			<xsl:apply-templates select="$eq" mode="eq_tp">
				<xsl:with-param name="tp" select="$tp"/>
				<xsl:with-param name="eq_file_name" select="$eq_sn"/>
				<xsl:with-param name="tp_file_name" select="$tp_sn"/>
			</xsl:apply-templates>
		</xsl:variable>





		<!-- EQ, TP and boundary objects (CN, TN and entsoe:EnergySchedulingType) merged -->
		<xsl:variable name="eq_tp_bd">
			<xsl:apply-templates select="$eq_tp" mode="eq_tp_bd">
				<xsl:with-param name="eq_tp" select="$eq_tp"/>
				<xsl:with-param name="eqbd" select="$eqbd"/>
				<xsl:with-param name="tpbd" select="$tpbd"/>
				<xsl:with-param name="eqbd_file_name" select="$eqbd_sn"/>
				<xsl:with-param name="tpbd_file_name" select="$tpbd_sn"/>
			</xsl:apply-templates>
		</xsl:variable>


		<!-- Merge the rest of the boundary (continers, regions and BaseVoltages) -->
		<xsl:variable name="eq_tp_bd_wcont">
			<xsl:apply-templates select="$eq_tp_bd" mode="eq_tp_bd_wcont">
				<xsl:with-param name="eq_tp_bd" select="$eq_tp_bd"/>
				<xsl:with-param name="eqbd" select="$eqbd"/>
			</xsl:apply-templates>
		</xsl:variable>
		<!-- Add SSH and SV -->
		<xsl:variable name="eq_tp_bd_ssh_sv">
			<xsl:apply-templates select="$eq_tp_bd_wcont" mode="ssh_sv">
				<xsl:with-param name="ssh" select="fn:parse-xml($SSH)"/>
				<xsl:with-param name="sv" select="fn:parse-xml($SV)"/>
				<xsl:with-param name="ssh_file_name" select="$ssh_sn"/>
				<xsl:with-param name="sv_file_name" select="$sv_sn"/>
			</xsl:apply-templates>
		</xsl:variable>
		<!--<xsl:result-document href="{$result_file_name}">
			&lt;!&ndash;xsl:copy-of select="$eq_tp"/&ndash;&gt;
			&lt;!&ndash;xsl:copy-of select="$eq_tp_bd"/&ndash;&gt;
			&lt;!&ndash;xsl:copy-of select="$eq_tp_bd_wcont"/&ndash;&gt;
			<xsl:copy-of select="$eq_tp_bd_ssh_sv"/>
		</xsl:result-document>-->
		<xsl:variable name="merged_IGM">
			<xsl:copy-of select="$eq_tp_bd_ssh_sv"/>
		</xsl:variable>
		<xsl:copy-of select="$merged_IGM"/>




	</xsl:template>

	<!-- EQTP -->
	<xsl:template match="rdf:RDF" mode="eq_tp" priority="2">
		<xsl:param name="tp"/>
		<xsl:param name="eq_file_name"/>
		<xsl:param name="tp_file_name"/>
		<xsl:copy>
			<xsl:namespace name="brlnd">http://brolunda.com/ecore-converter#</xsl:namespace>
			<xsl:copy-of select="@*"/>
			<xsl:apply-templates select="md:FullModel">
				<xsl:with-param name="file_name" select="$eq_file_name"/>
			</xsl:apply-templates>
			<xsl:apply-templates select="$tp/rdf:RDF/md:FullModel">
				<xsl:with-param name="file_name" select="$tp_file_name"/>
			</xsl:apply-templates>
			<xsl:apply-templates select="*[fn:name() != 'md:FullModel']" mode="eq_tp">
				<xsl:with-param name="tp_objects" select="$tp/rdf:RDF"/>
			</xsl:apply-templates>
			<xsl:apply-templates select="$tp/rdf:RDF/*[@rdf:about and fn:name() != 'md:FullModel'] " mode="eq_tp_reverse">
				<xsl:with-param name="eq_objects" select="."/>
			</xsl:apply-templates>
			<xsl:comment>TopologicalNodes from TP</xsl:comment>
			<xsl:apply-templates select="$tp/rdf:RDF/cim:TopologicalNode" mode="tp"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="*" mode="eq_tp_reverse">
		<xsl:param name="eq_objects"/>
		<xsl:variable name="current" select="."/>
		<xsl:variable name="eq_obj" select="fn:key('id', fn:translate($current/@rdf:about, '#', ''), $eq_objects)"/>
		<xsl:if test="fn:not($eq_obj)">
			<xsl:comment>TP object not in eq</xsl:comment>
			<xsl:copy>
				<xsl:attribute name="rdf:ID" select="fn:translate($current/@rdf:about, '#', '')"/>
				<xsl:copy-of select="*"/>
			</xsl:copy>
		</xsl:if>
	</xsl:template>

	<xsl:template match="*" mode="eq_tp" priority="1">
		<xsl:param name="tp_objects"/>
		<xsl:variable name="current" select="."/>
		<xsl:variable name="tp_obj" select="fn:key('about', fn:concat('#', $current/@rdf:ID), $tp_objects)"/>
		<xsl:copy>
			<xsl:copy-of select="@*"/>
			<xsl:apply-templates select="* | comment()" mode="attribute"/>
			<xsl:if test="$tp_obj">
				<xsl:comment>From TP</xsl:comment>
				<xsl:copy-of select="$tp_obj/*[fn:name() != 'cim:IdentifiedObject.name' ]"/>
			</xsl:if>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="*" mode="tp">
		<xsl:copy>
			<xsl:copy-of select="@*"/>
			<xsl:apply-templates select="* | comment()" mode="attribute"/>
		</xsl:copy>
	</xsl:template>

	<!-- EQTP with Boundary objects -->

	<xsl:template match="rdf:RDF" mode="eq_tp_bd">
		<xsl:param name="eq_tp"/>
		<xsl:param name="eqbd"/>
		<xsl:param name="tpbd"/>
		<xsl:param name="eqbd_file_name"/>
		<xsl:param name="tpbd_file_name"/>
		<xsl:copy>
			<xsl:copy-of select="@*"/>
			<xsl:apply-templates select="$eqbd/rdf:RDF/md:FullModel">
				<xsl:with-param name="file_name" select="$eqbd_file_name"/>
			</xsl:apply-templates>
			<xsl:apply-templates select="$tpbd/rdf:RDF/md:FullModel">
				<xsl:with-param name="file_name" select="$tpbd_file_name"/>
			</xsl:apply-templates>
			<xsl:copy-of select="* | comment()"/>
			<xsl:comment>EQ boundary objects</xsl:comment>
			<xsl:copy-of select="$eqbd/rdf:RDF/entsoe:EnergySchedulingType"/>
			<xsl:apply-templates select="$eqbd/rdf:RDF/cim:ConnectivityNode" mode="eq_tp_bd">
				<xsl:with-param name="eq_tp" select="$eq_tp"/>
				<xsl:with-param name="tpbd" select="$tpbd"/>
			</xsl:apply-templates>
			<xsl:comment>TP boundary objects</xsl:comment>
			<xsl:apply-templates select="$tpbd/rdf:RDF/cim:TopologicalNode" mode="eq_tp_bd">
				<xsl:with-param name="eq_tp" select="$eq_tp"/>
			</xsl:apply-templates>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="cim:ConnectivityNode" mode="eq_tp_bd">
		<xsl:param name="eq_tp"/>
		<xsl:param name="tpbd"/>
		<xsl:variable name="current" select="."/>
		<xsl:variable name="eq_tp_objects" select="fn:key('resource', fn:concat('#', $current/@rdf:ID), $eq_tp/rdf:RDF)"/>
		<xsl:variable name="cn_in_tp" select="fn:key('about', fn:concat('#', $current/@rdf:ID), $tpbd/rdf:RDF)"/>
		<xsl:if test="$eq_tp_objects">
			<xsl:copy>
				<xsl:copy-of select="@*"/>
				<xsl:copy-of select="*"/>
				<xsl:if test="eq_tp_objects">
					<xsl:comment>From TP</xsl:comment>
					<xsl:copy-of select="$eq_tp_objects/*"/>
				</xsl:if>
			</xsl:copy>
		</xsl:if>
	</xsl:template>

	<xsl:template match="cim:TopologicalNode" mode="eq_tp_bd">
		<xsl:param name="eq_tp"/>
		<xsl:param name="eqbd"/>
		<xsl:variable name="current" select="."/>
		<xsl:variable name="eq_tp_objects" select="fn:key('resource', fn:concat('#', $current/@rdf:ID), $eq_tp/rdf:RDF)"/>
		<xsl:if test="$eq_tp_objects">
			<xsl:copy-of select=". | comment()"/>
		</xsl:if>
	</xsl:template>

	<!-- EQTP with Boundary objects including boundary containers -->

	<xsl:template match="rdf:RDF" mode="eq_tp_bd_wcont">
		<xsl:param name="eq_tp_bd"/>
		<xsl:param name="eqbd"/>
		<xsl:copy>
			<xsl:copy-of select="@*"/>
			<xsl:copy-of select="* | comment()"/>
			<xsl:comment>EQ boundary containers</xsl:comment>
			<xsl:apply-templates select="$eqbd/rdf:RDF/cim:Line
										| $eqbd/rdf:RDF/cim:GeographicalRegion
										| $eqbd/rdf:RDF/cim:SubGeographicalRegion
										| $eqbd/rdf:RDF/cim:BaseVoltage" mode="eq_tp_bd_wcont">
				<xsl:with-param name="eq_tp_bd" select="$eq_tp_bd"/>
			</xsl:apply-templates>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="cim:SubGeographicalRegion | cim:GeographicalRegion" mode="eq_tp_bd_wcont">
		<xsl:copy-of select="."/>
	</xsl:template>

	<xsl:template match="cim:Line | cim:BaseVoltage" mode="eq_tp_bd_wcont">
		<xsl:param name="eq_tp_bd"/>
		<xsl:variable name="current" select="."/>
		<xsl:variable name="eq_tp_bd_obj" select="fn:key('resource', fn:concat('#', $current/@rdf:ID), $eq_tp_bd/rdf:RDF)"/>
		<xsl:if test="$eq_tp_bd_obj">
			<xsl:copy-of select="."/>
		</xsl:if>
	</xsl:template>

	<!-- SSH and SV -->
	<xsl:template match="/rdf:RDF" mode="ssh_sv" priority="2">
		<xsl:param name="ssh"/>
		<xsl:param name="sv"/>
		<xsl:param name="ssh_file_name"/>
		<xsl:param name="sv_file_name"/>
		<xsl:variable name="ssh_reverse">
			<xsl:apply-templates select="$ssh/rdf:RDF/*[@rdf:about and fn:name() != 'md:FullModel']" mode="ssh_sv_reverse">
				<xsl:with-param name="eq_objects" select="."/>
			</xsl:apply-templates>
		</xsl:variable>
		<xsl:variable name="sv_reverse">
			<xsl:apply-templates select="$sv/rdf:RDF/*[@rdf:about and fn:name() != 'md:FullModel']" mode="ssh_sv_reverse">
				<xsl:with-param name="eq_objects" select="."/>
			</xsl:apply-templates>
		</xsl:variable>
		<xsl:copy>
			<xsl:copy-of select="@*"/>
			<xsl:apply-templates select="$sv/rdf:RDF/md:FullModel">
				<xsl:with-param name="file_name" select="$sv_file_name"/>
			</xsl:apply-templates>
			<xsl:apply-templates select="$ssh/rdf:RDF/md:FullModel">
				<xsl:with-param name="file_name" select="$ssh_file_name"/>
			</xsl:apply-templates>
			<xsl:apply-templates select="* | comment()" mode="ssh_sv">
				<xsl:with-param name="ssh" select="$ssh"/>
				<xsl:with-param name="sv" select="$sv"/>
			</xsl:apply-templates>
			<xsl:comment>Complete objects from SV</xsl:comment>
			<xsl:copy-of select="$sv/rdf:RDF/*[@rdf:ID]"/>
			<xsl:comment>Partial incomplete objects in SV and SSH</xsl:comment>
			<xsl:call-template name="merge_on_ids">
				<xsl:with-param name="objects1" select="$ssh_reverse/*"/>
				<xsl:with-param name="objects2" select="$sv_reverse/*"/>
				<xsl:with-param name="objects1_type" select=" 'SSH' "/>
				<xsl:with-param name="objects2_type" select=" 'SV' "/>
			</xsl:call-template>
		</xsl:copy>
	</xsl:template>

	<xsl:template name="merge_on_ids">
		<xsl:param name="objects1"/>
		<xsl:param name="objects2"/>
		<xsl:param name="objects1_type"/>
		<xsl:param name="objects2_type"/>
		<xsl:variable name="object1" select="$objects1[1]"/>
		<xsl:variable name="object2" select="$objects2[@rdf:ID = $object1/@rdf:ID]"/>
		<xsl:choose>
			<xsl:when test="fn:not($objects1) and fn:not($objects2)"/>
			<xsl:when test="fn:not($objects1) and $objects2">
				<xsl:comment><xsl:value-of select="$objects2_type"/> objects not in eq</xsl:comment>
				<xsl:copy-of select="$objects2"/>
			</xsl:when>
			<xsl:when test="$objects1 and fn:not($objects2)">
				<xsl:comment><xsl:value-of select="$objects1_type"/> objects not in eq</xsl:comment>
				<xsl:copy-of select="$objects1"/>
			</xsl:when>
			<xsl:when test="$object1 and fn:not($object2)">
				<xsl:comment><xsl:value-of select="$objects1_type"/> object not in eq</xsl:comment>
				<xsl:copy-of select="$object1"/>
				<xsl:call-template name="merge_on_ids">
					<xsl:with-param name="objects1" select="$objects1[fn:position() != 1]"/>
					<xsl:with-param name="objects2" select="$objects2"/>
					<xsl:with-param name="objects1_type" select="$objects1_type"/>
					<xsl:with-param name="objects2_type" select="$objects2_type"/>
				</xsl:call-template>
			</xsl:when>
			<xsl:when test="$object1 and $object2">
				<xsl:comment>Combined object not in eq</xsl:comment>
				<xsl:element name="{fn:name($object1)}">
					<xsl:copy-of select="$object1/@rdf:ID"/>
					<xsl:comment><xsl:value-of select="$objects1_type"/> object not in eq</xsl:comment>
					<xsl:copy-of select="$object1/*"/>
					<xsl:comment><xsl:value-of select="$objects2_type"/> object not in eq</xsl:comment>
					<xsl:copy-of select="$object2/*"/>
				</xsl:element>
				<xsl:call-template name="merge_on_ids">
					<xsl:with-param name="objects1" select="$objects1[fn:position() != 1]"/>
					<xsl:with-param name="objects2" select="$objects2[@rdf:ID != $object1/@rdf:ID]"/>
					<xsl:with-param name="objects1_type" select="$objects1_type"/>
					<xsl:with-param name="objects2_type" select="$objects2_type"/>
				</xsl:call-template>
			</xsl:when>
		</xsl:choose>
	</xsl:template>

	<xsl:template match="*" mode="ssh_sv_reverse">
		<xsl:param name="eq_objects"/>
		<xsl:variable name="current" select="."/>
		<xsl:variable name="eq_obj" select="fn:key('id', fn:translate($current/@rdf:about, '#', ''), $eq_objects)"/>
		<xsl:if test="fn:not($eq_obj)">
			<xsl:copy>
				<xsl:attribute name="rdf:ID" select="fn:translate($current/@rdf:about, '#', '')"/>
				<xsl:copy-of select="*"/>
			</xsl:copy>
		</xsl:if>
	</xsl:template>

	<xsl:template match="*" mode="ssh_sv" priority="1">
		<xsl:param name="ssh"/>
		<xsl:param name="sv"/>
		<xsl:variable name="current" select="."/>
		<xsl:variable name="ssh_object" select="fn:key('about', fn:concat('#', $current/@rdf:ID), $ssh/rdf:RDF)"/>
		<xsl:variable name="sv_object" select="fn:key('about', fn:concat('#', $current/@rdf:ID), $sv/rdf:RDF)"/>
		<xsl:copy>
			<xsl:copy-of select="@*"/>
			<xsl:copy-of select="* | comment()"/>
			<xsl:if test="$ssh_object">
				<xsl:comment>From SSH</xsl:comment>
				<xsl:copy-of select="$ssh_object/*[fn:name() != 'cim:IdentifiedObject.name' ] | comment()"/>
			</xsl:if>
			<xsl:if test="$sv_object">
				<xsl:comment>From SV</xsl:comment>
				<xsl:copy-of select="$sv_object/* | comment()"/>
			</xsl:if>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="comment()" mode="ssh_sv" priority="1">
		<xsl:copy-of select="."/>
	</xsl:template>

	<!-- Common -->

	<xsl:template match="*" priority="1" mode="attribute">
		<xsl:copy>
			<xsl:copy-of select="@*"/>
			<xsl:apply-templates mode="attribute"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="comment()" mode="attribute">
		<xsl:copy-of select="."/>
	</xsl:template>

	<!-- FullModel -->

	<xsl:template match="md:FullModel">
		<xsl:param name="file_name"/>
		<xsl:variable name="_fn" select="fn:translate($file_name, '1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-.,', '')"/>
		<xsl:variable name="nr_fn" select="fn:string-length($_fn)"/>
		<xsl:variable name="effectiveDateTime" select="fn:substring-before($file_name, '_')"/>
		<xsl:variable name="businessProcess">
			<xsl:if test="$nr_fn = 3">Other</xsl:if>
			<xsl:if test="$nr_fn = 4">
				<xsl:value-of select="fn:substring-before(fn:substring-after($file_name, '_'), '_')"/>
			</xsl:if>
		</xsl:variable>
		<xsl:variable name="sourcingActor">
			<xsl:if test="$nr_fn = 3">
				<xsl:value-of select="fn:substring-before(fn:substring-after($file_name, '_'), '_')"/>
			</xsl:if>
			<xsl:if test="$nr_fn = 4">
				<xsl:value-of select="fn:substring-before(fn:substring-after(fn:substring-after($file_name, '_'), '_'), '_')"/>
			</xsl:if>
		</xsl:variable>
		<xsl:variable name="modelPart">
			<xsl:if test="$nr_fn = 3">
				<xsl:value-of select="fn:substring-before(fn:substring-after(fn:substring-after($file_name, '_'), '_'), '_')"/>
			</xsl:if>
			<xsl:if test="$nr_fn = 4">
				<xsl:value-of select="fn:substring-before(fn:substring-after(fn:substring-after(fn:substring-after($file_name, '_'), '_'), '_'), '_')"/>
			</xsl:if>
		</xsl:variable>
		<xsl:variable name="fileVersion">
			<xsl:if test="$nr_fn = 3">
				<xsl:value-of  select="fn:substring-after(fn:substring-after(fn:substring-after($file_name, '_'), '_'), '_')"/>
			</xsl:if>
			<xsl:if test="$nr_fn = 4">
				<xsl:value-of select="fn:substring-after(fn:substring-after(fn:substring-after(fn:substring-after($file_name, '_'), '_'), '_'), '_')"/>
			</xsl:if>
		</xsl:variable>
		<xsl:variable name="dash_sa" select="fn:translate($sourcingActor, '1234567890ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvxyz.,', '')"/>
		<xsl:variable name="nr_dash_sa" select="fn:string-length($dash_sa)"/>
		<xsl:variable name="sourcingTSO">
			<xsl:if test="$nr_dash_sa = 0"><xsl:value-of  select="$sourcingActor"/></xsl:if>
			<xsl:if test="$nr_dash_sa = 2"><xsl:value-of select="fn:substring-after(fn:substring-after($sourcingActor, '_'), '_')"/></xsl:if>
		</xsl:variable>
		<xsl:variable name="sourcingRSC">
			<xsl:if test="$nr_dash_sa = 1 or $nr_dash_sa = 2"><xsl:value-of select="fn:substring-before($sourcingActor, '_')"/></xsl:if>
		</xsl:variable>
		<xsl:variable name="synchronousArea">
			<xsl:if test="$nr_dash_sa = 1 or $nr_dash_sa = 2"><xsl:value-of select="fn:substring-before(fn:substring-after($sourcingActor, '_'), '_')"/></xsl:if>
		</xsl:variable>
		<xsl:variable name="bp" select="md:Model.description/BP"/>
		<xsl:variable name="tool" select="md:Model.description/TOOL"/>
		<xsl:variable name="rsc" select="md:Model.description/RSC"/>
		<xsl:variable name="txt" select="md:Model.description/TXT"/>
		<xsl:copy>
			<xsl:copy-of select="@*"/>
			<xsl:copy-of select="*[fn:name() != 'md:Model.description' ]"/>
			<xsl:element name="md:Model.description">
				<xsl:if test="md:Model.description/TXT != '' "><xsl:value-of select="md:Model.description/TXT"/></xsl:if>
				<xsl:if test="fn:not(md:Model.description/BP)
								and fn:not(md:Model.description/TOOL)
								and fn:not(md:Model.description/RSC)
								and fn:not(md:Model.description/TXT)"><xsl:value-of select="md:Model.description"/></xsl:if>
			</xsl:element>
			<xsl:element name="brlnd:Model.region"><xsl:value-of select="fn:substring-after(md:Model.modelingAuthoritySet, '?Region=')"/></xsl:element>
			<xsl:element name="brlnd:Model.bp"><xsl:value-of select="md:Model.description/BP"/></xsl:element>
			<xsl:element name="brlnd:Model.tool"><xsl:value-of select="md:Model.description/TOOL"/></xsl:element>
			<xsl:element name="brlnd:Model.rsc"><xsl:value-of select="md:Model.description/TXT"/></xsl:element>
			<xsl:element name="brlnd:Model.effectiveDateTime"><xsl:value-of select="$effectiveDateTime"/></xsl:element>
			<xsl:element name="brlnd:Model.businessProcess"><xsl:value-of select="$businessProcess"/></xsl:element>
			<xsl:element name="brlnd:Model.sourcingTSO"><xsl:value-of select="$sourcingActor"/></xsl:element>
			<xsl:element name="brlnd:Model.modelPart"><xsl:value-of select="$modelPart"/></xsl:element>
			<xsl:element name="brlnd:Model.fileVersion"><xsl:value-of select="$fileVersion"/></xsl:element>
			<xsl:element name="brlnd:Model.sourcingRSC"><xsl:value-of select="$sourcingRSC"/></xsl:element>
			<xsl:element name="brlnd:Model.synchronousArea"><xsl:value-of select="$synchronousArea"/></xsl:element>
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>