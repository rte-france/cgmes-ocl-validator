<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns:xs="http://www.w3.org/2001/XMLSchema" 
	xmlns:fn="http://www.w3.org/2005/xpath-functions" 
	xmlns:xmi="http://www.omg.org/spec/XMI/20131001" 
	xmlns:uml="http://www.omg.org/spec/UML/20131001" 
	xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore" 
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
	xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"  
	xmlns:md="http://iec.ch/TC57/61970-552/ModelDescription/1#" 
	xmlns:cim="http://iec.ch/TC57/2013/CIM-schema-cim16#"
	xmlns:entsoe="http://entsoe.eu/CIM/SchemaExtension/3/1#"
	xmlns:brlnd="http://brolunda.com/ecore-converter#"
	xmlns:CGMES = "http://Model/1.0/CGMES" >
	<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes"/>
<!--
This script was created 2018-10-29 by Lars-Ola Österlund/Brolunda Consulting.

The script convert a cimxml file to xmi that can be read by EMF.

The script is controlled by a control file; cim16_analyse_igm.xml or cim16_analyse_igm_cxtp.xml
The control files describe the inputs to script as well as where the result is saved.

Change history
2018-10-29 LOO New file
2018-11-05 LOO Updated to support DataSet and DataSetMember
2019-02-26 LOO Updated to support all profiles and model
2019-06-14 LOO Updated to add object references to model
2019-08-01 LOO Rule level 1 and 2 enumerations added as data
-->
	
	<xsl:key name="mrid" match="/mrid2indexroot/*" use="@mrid"/>
	
	<!--<xsl:template match="/rdf:RDF">
		<xsl:apply-templates select="BATCH"/>
	</xsl:template>
	
	<xsl:template match="BATCH">
		<xsl:apply-templates select="cim16_create_xmi_from_cimxml">
			<xsl:with-param name="batch" select="."/>		
		</xsl:apply-templates>
	</xsl:template>
-->
	<xsl:template match="/rdf:RDF" priority="2">
		<xsl:apply-templates select="ACTION"/>
	</xsl:template>

	<xsl:template match="ACTION" priority="2">
		<xsl:apply-templates select="CREATE_XMI"/>
	</xsl:template>

	<xsl:template match="CREATE_XMI" priority="2">
		<xsl:apply-templates select="CREATE_XMI_FROM_CIMXML"/>

	</xsl:template>

	<xsl:param name="merged_xml"/>
	<xsl:param name="ecore"/>
	<xsl:param name="ecore_name"/>
	<xsl:param name="type"/>
	
	<xsl:template match="CREATE_XMI_FROM_CIMXML">
		<!--<xsl:param name="batch"/>
		<xsl:variable name="current" select="."/>
		<xsl:variable name="file_name_from" select="$batch/*[name() = $current/@file_name_from]"/>
		<xsl:variable name="input_file_name" select="fn:concat('BATCH', $batch/@nr, '/', fn:substring-before($file_name_from, '.xml'), '_', @suffix, '.xml')"/>
		<xsl:variable name="input_dir">
			<xsl:call-template name="get_base_directory">
				<xsl:with-param name="path" select="base-uri(.)"/>
			</xsl:call-template>
		</xsl:variable>-->
		<xsl:variable name="input" select="fn:root($merged_xml)"/>
		<!--<xsl:variable name="result_file_name" select="fn:concat(fn:substring-before($input_file_name, '.xml'), '.xmi')"/>-->
		<xsl:variable name="im" select="fn:parse-xml($ecore)/ecore:EPackage"/>
		<xsl:variable name="data_root" select="IM_PACKAGE[@data_root]/@data_root"/>
		<xsl:variable name="im_package_name" select="IM_PACKAGE[@scope = 'tree']/@package"/>
		<xsl:variable name="mrid2index">
			<xsl:element name="mrid2indexroot">
				<xsl:apply-templates select="$input/rdf:RDF/*" mode="mrid2index"/>
			</xsl:element>
		</xsl:variable>

		<xsl:variable name="result">
			<xsl:element name="{$data_root}">
				<xsl:namespace name="xmi">http://www.omg.org/spec/XMI/20131001</xsl:namespace>
				<xsl:namespace name="xsi">http://www.w3.org/2001/XMLSchema-instance</xsl:namespace>
				<xsl:namespace name="md">http://iec.ch/TC57/61970-552/ModelDescription/1#</xsl:namespace>
				<xsl:namespace name="cim">http://iec.ch/TC57/2013/CIM-schema-cim16#</xsl:namespace>
				<xsl:namespace name="entsoe">http://entsoe.eu/CIM/SchemaExtension/3/1#</xsl:namespace>
				<xsl:namespace name="xs">http://www.w3.org/2001/XMLSchema</xsl:namespace>
				<xsl:namespace name="fn">http://www.w3.org/2005/xpath-functions</xsl:namespace>
				<xsl:namespace name="ecore">http://www.eclipse.org/emf/2002/Ecore</xsl:namespace>
				<xsl:namespace name="uml">http://www.omg.org/spec/UML/20131001</xsl:namespace>
				<xsl:namespace name="rdf">http://www.w3.org/1999/02/22-rdf-syntax-ns#</xsl:namespace>
				<xsl:namespace name="brlnd">http://brolunda.com/ecore-converter#</xsl:namespace>
				<xsl:apply-templates select="IM_PACKAGE" mode="namespace">
					<xsl:with-param name="im" select="$im"/>
				</xsl:apply-templates>
				<xsl:attribute name="xsi:schemaLocation">
					<xsl:apply-templates select="IM_PACKAGE" mode="schema_location">
						<xsl:with-param name="im" select="$im"/>
						<xsl:with-param name="im_file_name" select="$ecore_name"/>
					</xsl:apply-templates>
				</xsl:attribute>
				<xsl:attribute name="type" select="$type"/>
				<xsl:attribute name="validationScope" select="@validationScope"/>
				<xsl:attribute name="excludeProvedRules" select="@excludeProvedRules"/>
				<xsl:attribute name="local_level_validation" select="@local_level_validation"/>
				<xsl:attribute name="global_level_validation" select="@global_level_validation"/>
				<xsl:attribute name="emf_level_validation" select="@emf_level_validation"/>
				<xsl:attribute name="isEQoperation">
					<xsl:choose>
						<xsl:when test="$input/rdf:RDF/md:FullModel/md:Model.profile[. = 'http://entsoe.eu/CIM/EquipmentOperation/3/1']">true</xsl:when>
						<xsl:otherwise>false</xsl:otherwise>
					</xsl:choose>
				</xsl:attribute>
				<xsl:attribute name="isEQshortCircuit">
					<xsl:choose>
						<xsl:when test="$input/rdf:RDF/md:FullModel/md:Model.profile[. = 'http://entsoe.eu/CIM/EquipmentShortCircuit/3/1']">true</xsl:when>
						<xsl:otherwise>false</xsl:otherwise>
					</xsl:choose>
				</xsl:attribute>
				<!-- Level 1 and 2 enumerations -->
				<regions>DKE</regions>
				<regions>DKW</regions>
				<regions>NL</regions>
				<regions>DE</regions>
				<businessProcesses>TY</businessProcesses>
				<businessProcesses>YR</businessProcesses>
				<businessProcesses>MO</businessProcesses>
				<businessProcesses>WK</businessProcesses>
				<businessProcesses>2D</businessProcesses>
				<businessProcesses>1D</businessProcesses>
				<businessProcesses>ID</businessProcesses>
				<businessProcesses>31</businessProcesses>
				<businessProcesses>30</businessProcesses>
				<businessProcesses>29</businessProcesses>
				<businessProcesses>28</businessProcesses>
				<businessProcesses>27</businessProcesses>
				<businessProcesses>26</businessProcesses>
				<businessProcesses>25</businessProcesses>
				<businessProcesses>24</businessProcesses>
				<businessProcesses>23</businessProcesses>
				<businessProcesses>22</businessProcesses>
				<businessProcesses>21</businessProcesses>
				<businessProcesses>20</businessProcesses>
				<businessProcesses>19</businessProcesses>
				<businessProcesses>18</businessProcesses>
				<businessProcesses>17</businessProcesses>
				<businessProcesses>16</businessProcesses>
				<businessProcesses>15</businessProcesses>
				<businessProcesses>14</businessProcesses>
				<businessProcesses>13</businessProcesses>
				<businessProcesses>12</businessProcesses>
				<businessProcesses>11</businessProcesses>
				<businessProcesses>10</businessProcesses>
				<businessProcesses>09</businessProcesses>
				<businessProcesses>08</businessProcesses>
				<businessProcesses>07</businessProcesses>
				<businessProcesses>06</businessProcesses>
				<businessProcesses>05</businessProcesses>
				<businessProcesses>04</businessProcesses>
				<businessProcesses>03</businessProcesses>
				<businessProcesses>02</businessProcesses>
				<businessProcesses>01</businessProcesses>
				<businessProcesses>RT</businessProcesses>
				<businessProcesses>Other</businessProcesses>
				<modelingAuthorities>balticrsc</modelingAuthorities>
				<modelingAuthorities>coreso</modelingAuthorities>
				<modelingAuthorities>entsoe</modelingAuthorities>
				<modelingAuthorities>nordicrsc</modelingAuthorities>
				<modelingAuthorities>sccrsci</modelingAuthorities>
				<modelingAuthorities>tscnet</modelingAuthorities>
				<modelingAuthorities>50hertz</modelingAuthorities>
				<modelingAuthorities>amprion</modelingAuthorities>
				<modelingAuthorities>energinet</modelingAuthorities>
				<modelingAuthorities>apg</modelingAuthorities>
				<modelingAuthorities>ast</modelingAuthorities>
				<modelingAuthorities>ceps</modelingAuthorities>
				<modelingAuthorities>cges</modelingAuthorities>
				<modelingAuthorities>creosnet</modelingAuthorities>
				<modelingAuthorities>elering</modelingAuthorities>
				<modelingAuthorities>eles</modelingAuthorities>
				<modelingAuthorities>elia</modelingAuthorities>
				<modelingAuthorities>ems</modelingAuthorities>
				<modelingAuthorities>eso</modelingAuthorities>
				<modelingAuthorities>fingrid</modelingAuthorities>
				<modelingAuthorities>hops</modelingAuthorities>
				<modelingAuthorities>admie</modelingAuthorities>
				<modelingAuthorities>kostt</modelingAuthorities>
				<modelingAuthorities>litgrid</modelingAuthorities>
				<modelingAuthorities>mavir</modelingAuthorities>
				<modelingAuthorities>mepso</modelingAuthorities>
				<modelingAuthorities>nationalgrid</modelingAuthorities>
				<modelingAuthorities>nosbih</modelingAuthorities>
				<modelingAuthorities>ost</modelingAuthorities>
				<modelingAuthorities>pse</modelingAuthorities>
				<modelingAuthorities>ree</modelingAuthorities>
				<modelingAuthorities>ren</modelingAuthorities>
				<modelingAuthorities>rtefrance</modelingAuthorities>
				<modelingAuthorities>sepsas</modelingAuthorities>
				<modelingAuthorities>statnett</modelingAuthorities>
				<modelingAuthorities>soni</modelingAuthorities>
				<modelingAuthorities>svk</modelingAuthorities>
				<modelingAuthorities>swissgrid</modelingAuthorities>
				<modelingAuthorities>teias</modelingAuthorities>
				<modelingAuthorities>tennet</modelingAuthorities>
				<modelingAuthorities>terna</modelingAuthorities>
				<modelingAuthorities>transelectrica</modelingAuthorities>
				<modelingAuthorities>transnetbw</modelingAuthorities>
				<modelingAuthorities>ukrenergo</modelingAuthorities>
				<modelingAuthorities>vuen</modelingAuthorities>
				<modelingAuthorities>alegro</modelingAuthorities>
				<modelingAuthorities>balticcable</modelingAuthorities>
				<modelingAuthorities>britned</modelingAuthorities>
				<modelingAuthorities>cobracable</modelingAuthorities>
				<modelingAuthorities>eleclink</modelingAuthorities>
				<modelingAuthorities>ewic</modelingAuthorities>
				<modelingAuthorities>fennoskan</modelingAuthorities>
				<modelingAuthorities>franceitaly</modelingAuthorities>
				<modelingAuthorities>ifa1interconnector</modelingAuthorities>
				<modelingAuthorities>inelfe</modelingAuthorities>
				<modelingAuthorities>italygreece</modelingAuthorities>
				<modelingAuthorities>kontek</modelingAuthorities>
				<modelingAuthorities>kontiskan</modelingAuthorities>
				<modelingAuthorities>kriegersflakcgs</modelingAuthorities>
				<modelingAuthorities>litpol</modelingAuthorities>
				<modelingAuthorities>moyle</modelingAuthorities>
				<modelingAuthorities>nemo</modelingAuthorities>
				<modelingAuthorities>nord</modelingAuthorities>
				<modelingAuthorities>nordbalt</modelingAuthorities>
				<modelingAuthorities>norned</modelingAuthorities>
				<modelingAuthorities>skagerrak</modelingAuthorities>
				<modelingAuthorities>storebaelt</modelingAuthorities>
				<modelingAuthorities>swepol</modelingAuthorities>
				<modelingAuthorities>sydvastlanken</modelingAuthorities>
				<modelingAuthorities>monita</modelingAuthorities>
				<modelingAuthorities>grita</modelingAuthorities>
				<modelingAuthorities>pisa</modelingAuthorities>
				<modelingAuthorities>sacoi</modelingAuthorities>
				<modelingAuthorities>sapei</modelingAuthorities>
				<modelingAuthorities>dsm</modelingAuthorities>
				<modelingAuthorities>scottishpower</modelingAuthorities>
				<modelingAuthorities>sse</modelingAuthorities>
				<modelingAuthorities>eirgrid</modelingAuthorities>
				<modelingAuthorities>landsnet</modelingAuthorities>
				<modelingAuthorities>moldelectrica</modelingAuthorities>
				<modelingAuthorities>belenergo</modelingAuthorities>
				<modelingAuthorities>sonelgaz</modelingAuthorities>
				<modelingAuthorities>iec</modelingAuthorities>
				<modelingAuthorities>gecol</modelingAuthorities>
				<modelingAuthorities>nepco</modelingAuthorities>
				<modelingAuthorities>steg</modelingAuthorities>
				<modelingAuthorities>one</modelingAuthorities>
				<modelingAuthorities>fskees</modelingAuthorities>
				<synchronousAreas>PEU</synchronousAreas>
				<synchronousAreas>CEU</synchronousAreas>
				<synchronousAreas>BAL</synchronousAreas>
				<synchronousAreas>SCN</synchronousAreas>
				<synchronousAreas>IRE</synchronousAreas>
				<synchronousAreas>UK</synchronousAreas>
				<mas>http://www.baltic-rsc.eu/OperationalPlanning</mas>
				<mas>http://www.coreso.eu/OperationalPlanning</mas>
				<mas>http://www.entsoe.eu/OperationalPlanning</mas>
				<mas>http://www.nordic-rsc.net/OperationalPlanning</mas>
				<mas>http://www.scc-rsci.com/OperationalPlanning</mas>
				<mas>http://www.tscnet.eu/OperationalPlanning</mas>
				<mas>http://www.50hertz.com/OperationalPlanning</mas>
				<mas>http://www.amprion.net/OperationalPlanning</mas>
				<mas>http://www.energinet.dk/OperationalPlanning?Region=DK1</mas>
				<mas>http://www.energinet.dk/OperationalPlanning?Region=DK2</mas>
				<mas>http://www.apg.at/OperationalPlanning</mas>
				<mas>http://www.ast.lv/OperationalPlanning</mas>
				<mas>http://www.ceps.cz/OperationalPlanning</mas>
				<mas>http://www.cges.me/OperationalPlanning</mas>
				<mas>http://www.creos-net.lu/OperationalPlanning</mas>
				<mas>http://www.elering.ee/OperationalPlanning</mas>
				<mas>http://www.eles.si/OperationalPlanning</mas>
				<mas>http://www.elia.be/OperationalPlanning</mas>
				<mas>http://www.ems.rs/OperationalPlanning</mas>
				<mas>http://www.eso.bg/OperationalPlanning</mas>
				<mas>http://www.fingrid.fi/OperationalPlanning</mas>
				<mas>http://www.hops.hr/OperationalPlanning</mas>
				<mas>http://www.admie.gr/OperationalPlanning</mas>
				<mas>http://www.kostt.com/OperationalPlanning</mas>
				<mas>http://www.litgrid.eu/OperationalPlanning</mas>
				<mas>http://www.mavir.hu/OperationalPlanning</mas>
				<mas>http://www.mepso.com.mk/OperationalPlanning</mas>
				<mas>http://www.nationalgrid.com/OperationalPlanning</mas>
				<mas>http://www.nosbih.ba/OperationalPlanning</mas>
				<mas>http://www.ost.al/OperationalPlanning</mas>
				<mas>http://www.pse.pl/OperationalPlanning</mas>
				<mas>http://www.ree.es/OperationalPlanning</mas>
				<mas>http://www.ren.pt/OperationalPlanning</mas>
				<mas>http://www.rte-france.com/OperationalPlanning</mas>
				<mas>http://www.sepsas.sk/OperationalPlanning</mas>
				<mas>http://www.statnett.no/OperationalPlanning</mas>
				<mas>http://www.soni.ltd.uk/OperationalPlanning</mas>
				<mas>http://www.svk.se/OperationalPlanning</mas>
				<mas>http://www.swissgrid.ch/OperationalPlanning</mas>
				<mas>http://www.teias.gov.tr/OperationalPlanning</mas>
				<mas>http://www.tennet.eu/OperationalPlanning?Region=DE</mas>
				<mas>http://www.tennet.eu/OperationalPlanning?Region=NL</mas>
				<mas>http://www.terna.it/OperationalPlanning</mas>
				<mas>http://www.transelectrica.ro/OperationalPlanning</mas>
				<mas>http://www.transnetbw.de/OperationalPlanning</mas>
				<mas>http://www.ukrenergo.energy.gov.ua/OperationalPlanning</mas>
				<mas>http://www.vuen.at/OperationalPlanning</mas>
				<mas>http://www.alegro.link/OperationalPlanning</mas>
				<mas>http://www.balticcable.com/OperationalPlanning</mas>
				<mas>http://www.britned.com/OperationalPlanning</mas>
				<mas>http://www.cobracable.eu/OperationalPlanning</mas>
				<mas>http://www.eleclink.co.uk/OperationalPlanning</mas>
				<mas>http://ewic.link/OperationalPlanning</mas>
				<mas>http://fennoskan.link/OperationalPlanning</mas>
				<mas>http://france-italy.link/OperationalPlanning</mas>
				<mas>http://ifa1interconnector.com/OperationalPlanning</mas>
				<mas>http://www.inelfe.eu/OperationalPlanning</mas>
				<mas>http://italy-greece.link/OperationalPlanning</mas>
				<mas>http://kontek.link/OperationalPlanning</mas>
				<mas>http://kontiskan.link/OperationalPlanning</mas>
				<mas>http://kriegersflakcgs.link/OperationalPlanning</mas>
				<mas>http://www.litpol.link/OperationalPlanning</mas>
				<mas>http://moyle.link/OperationalPlanning</mas>
				<mas>http://nemo.link/OperationalPlanning</mas>
				<mas>http://nord.link/OperationalPlanning</mas>
				<mas>http://nordbalt.link/OperationalPlanning</mas>
				<mas>http://norned.link/OperationalPlanning</mas>
				<mas>http://skagerrak.link/OperationalPlanning</mas>
				<mas>http://storebaelt.link/OperationalPlanning</mas>
				<mas>http://swepol.link/OperationalPlanning</mas>
				<mas>http://sydvastlanken.link/OperationalPlanning</mas>
				<mas>http://monita.link/OperationalPlanning</mas>
				<mas>http://grita.link/OperationalPlanning</mas>
				<mas>http://pisa.link/OperationalPlanning</mas>
				<mas>http://sacoi.link/OperationalPlanning</mas>
				<mas>http://sapei.link/OperationalPlanning</mas>
				<mas>http://www.dsm.org.cy/OperationalPlanning</mas>
				<mas>http://www.scottishpower.com/OperationalPlanning</mas>
				<mas>http://www.sse.com/OperationalPlanning</mas>
				<mas>http://www.eirgrid.com/OperationalPlanning</mas>
				<mas>http://www.landsnet.is/OperationalPlanning</mas>
				<mas>http://www.moldelectrica.md/OperationalPlanning</mas>
				<mas>http://www.belenergo.by/OperationalPlanning</mas>
				<mas>http://www.sonelgaz.dz/OperationalPlanning</mas>
				<mas>http://www.iec.co.il/OperationalPlanning</mas>
				<mas>http://www.gecol.ly/OperationalPlanning</mas>
				<mas>http://www.nepco.com.jo/OperationalPlanning</mas>
				<mas>http://www.steg.com.tn/OperationalPlanning</mas>
				<mas>http://www.one.org.ma/OperationalPlanning</mas>
				<mas>http://www.fsk-ees.ru/OperationalPlanning</mas>
				<TsoCodeList>ME</TsoCodeList>
				<TsoCodeList>LU</TsoCodeList>
				<TsoCodeList>MA</TsoCodeList>
				<TsoCodeList>BY</TsoCodeList>
				<TsoCodeList>RU</TsoCodeList>
				<TsoCodeList>GB</TsoCodeList>
				<TsoCodeList>LT</TsoCodeList>
				<TsoCodeList>MD</TsoCodeList>
				<TsoCodeList>SE</TsoCodeList>
				<TsoCodeList>NO</TsoCodeList>
				<TsoCodeList>AL</TsoCodeList>
				<TsoCodeList>BE</TsoCodeList>
				<TsoCodeList>CZ</TsoCodeList>
				<TsoCodeList>DE</TsoCodeList>
				<TsoCodeList>ES</TsoCodeList>
				<TsoCodeList>FR</TsoCodeList>
				<TsoCodeList>GR</TsoCodeList>
				<TsoCodeList>HR</TsoCodeList>
				<TsoCodeList>IT</TsoCodeList>
				<TsoCodeList>RS</TsoCodeList>
				<TsoCodeList>DK</TsoCodeList>
				<TsoCodeList>SI</TsoCodeList>
				<TsoCodeList>HU</TsoCodeList>
				<TsoCodeList>NL</TsoCodeList>
				<TsoCodeList>AT</TsoCodeList>
				<TsoCodeList>PT</TsoCodeList>
				<TsoCodeList>SK</TsoCodeList>
				<TsoCodeList>RO</TsoCodeList>
				<TsoCodeList>CH</TsoCodeList>
				<TsoCodeList>TR</TsoCodeList>
				<TsoCodeList>UA</TsoCodeList>
				<TsoCodeList>BG</TsoCodeList>
				<TsoCodeList>BA</TsoCodeList>
				<TsoCodeList>MK</TsoCodeList>
				<TsoCodeList>PL</TsoCodeList>
				<TsoCodeList>LV</TsoCodeList>
				<TsoCodeList>EE</TsoCodeList>
				<TsoCodeList>LT</TsoCodeList>
				<TsoCodeList>FI</TsoCodeList>
				<TsoCodeList>IE</TsoCodeList>
				<!--xsl:copy-of select="$mrid2index"/-->
				<xsl:apply-templates select="$input/rdf:RDF" mode="toxmi">
					<xsl:with-param name="im_package" select="$im//eSubpackages[@name = $im_package_name]"/>
					<xsl:with-param name="mrid2index" select="$mrid2index/*"/>
				</xsl:apply-templates>
			</xsl:element>
		</xsl:variable>

		<xsl:copy-of select="$result"/>

	</xsl:template>
	
	<xsl:template match="*" mode="mrid2index">
		<xsl:element name="{name()}">
			<xsl:attribute name="mrid" select="@rdf:ID | @rdf:about"/>
			<xsl:attribute name="pos" select="fn:position() - 1"/>
		</xsl:element>
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
	
	<xsl:template match="IM_PACKAGE" mode="namespace">
		<xsl:param name="im"/>
		<xsl:variable name="current" select="."/>
		<xsl:variable name="package" select="$im//eSubpackages[@name = $current/@package]"/>
		<xsl:if test="fn:not(@data_root)">
			<xsl:namespace name="{$package/@nsPrefix}" select="$package/@nsURI"/>
		</xsl:if>
		<xsl:apply-templates select="IM_PACKAGE" mode="namespace">
			<xsl:with-param name="im" select="$im"/>
		</xsl:apply-templates>
		<xsl:if test="@scope = 'tree' ">
			<xsl:apply-templates select="$im//eSubpackages[@name = $current/@package]" mode="namespace">
				<xsl:with-param name="im" select="$im"/>
			</xsl:apply-templates>
		</xsl:if>
	</xsl:template>
	
	<xsl:template match="eSubpackages" mode="namespace">
		<xsl:param name="im"/>
		<xsl:if test="*">
			<xsl:namespace name="{@nsPrefix}" select="@nsURI"/>
			<xsl:apply-templates select="eSubpackages" mode="namespace">
				<xsl:with-param name="im" select="$im"/>
			</xsl:apply-templates>
		</xsl:if>
	</xsl:template>
	
	<xsl:template match="IM_PACKAGE" mode="schema_location">
		<xsl:param name="im"/>
		<xsl:param name="im_file_name"/>
		<xsl:variable name="current" select="."/>
		<xsl:variable name="package" select="$im//eSubpackages[@name = $current/@package]"/>
		<xsl:value-of select="fn:concat($package/@nsURI, ' ', $im_file_name, '#/', fn:substring-after($package/@nsURI, $im/@nsURI), ' ')"/>
		<xsl:apply-templates select="IM_PACKAGE" mode="schema_location">
			<xsl:with-param name="im" select="$im"/>
			<xsl:with-param name="im_file_name" select="$im_file_name"/>
		</xsl:apply-templates>
		<xsl:if test="@scope = 'tree' ">
			<xsl:apply-templates select="$im//eSubpackages[@name = $current/@package]" mode="schema_location">
				<xsl:with-param name="im" select="$im"/>
				<xsl:with-param name="im_file_name" select="$im_file_name"/>
			</xsl:apply-templates>
		</xsl:if>
	</xsl:template>
	
	<xsl:template match="eSubpackages" mode="schema_location">
		<xsl:param name="im"/>
		<xsl:param name="im_file_name"/>
		<xsl:if test="*">
			<xsl:value-of select="fn:concat(@nsURI, ' ', $im_file_name, '#/', fn:substring-after(@nsURI, $im/@nsURI), ' ')"/>
			<xsl:apply-templates select="eSubpackages" mode="schema_location">
				<xsl:with-param name="im" select="$im"/>
				<xsl:with-param name="im_file_name" select="$im_file_name"/>
			</xsl:apply-templates>
		</xsl:if>
	</xsl:template>
	
	<xsl:template match="/rdf:RDF" mode="toxmi">
		<xsl:param name="im_package"/>
		<xsl:param name="mrid2index"/>
		<xsl:apply-templates select="*" mode="object">
			<xsl:with-param name="im_package" select="$im_package"/>
			<xsl:with-param name="mrid2index" select="$mrid2index"/>
		</xsl:apply-templates>
	</xsl:template>
	
	<xsl:template match="*" mode="object">
		<xsl:param name="im_package"/>
		<xsl:param name="mrid2index"/>
		<xsl:variable name="my_class_name" select="fn:substring-after(fn:name(), ':')"/>
		<xsl:variable name="ecore_object" select="$im_package//eClassifiers[@name = $my_class_name]"/>
		<xsl:variable name="container_name" select="$ecore_object/../@name"/>
		<xsl:variable name="key" select="fn:key('mrid', @rdf:ID, $mrid2index)"/>
		<xsl:element name="DataSetMember">
			<xsl:attribute name="xsi:type" select="fn:concat($container_name, ':', $my_class_name)"/>
			<xsl:attribute name="mRID" select="@rdf:ID|@rdf:about"/>
			<xsl:call-template name="convert_properties">
				<xsl:with-param name="all_prop" select="*"/>
				<xsl:with-param name="mrid2index" select="$mrid2index"/>
			</xsl:call-template>
			<xsl:value-of select="fn:position() - 1"/>
		</xsl:element>
		<xsl:if test="fn:count($key) > 1">
			<xsl:element name="{fn:name()}">
				<xsl:attribute name="rdf:ID" select="@rdf:ID"/>
				ERROR: Duplicates on ID 
			</xsl:element>
		</xsl:if>
	</xsl:template>
	
	<xsl:template name="convert_properties">
		<xsl:param name="all_prop"/>
		<xsl:param name="mrid2index"/>
		<xsl:variable name="current_prop" select="$all_prop[1]"/>
		<xsl:variable name="name" select="fn:substring-after(fn:name($current_prop), '.')"/>
		<xsl:variable name="value">
			<xsl:call-template name="populate_property_values">
				<xsl:with-param name="properties" select="$all_prop[fn:name() = fn:name($current_prop)]"/>
				<xsl:with-param name="mrid2index" select="$mrid2index"/>
				<xsl:with-param name="padd"></xsl:with-param>
			</xsl:call-template>
		</xsl:variable>
		<xsl:choose>
			<xsl:when test="fn:not($all_prop)"/>
			<xsl:otherwise>
				<xsl:if test="$name != 'Supersedes' or ($name = 'Supersedes' and $value != '')">
					<xsl:attribute name="{$name}" select="$value"/>
				</xsl:if>
				<xsl:call-template name="convert_properties">
					<xsl:with-param name="all_prop" select="$all_prop[fn:name() != fn:name($current_prop)]"/>
					<xsl:with-param name="mrid2index" select="$mrid2index"/>
				</xsl:call-template>
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
	
	<xsl:template name="populate_property_values">
		<xsl:param name="properties"/>
		<xsl:param name="mrid2index"/>
		<xsl:param name="padd"/>
		<xsl:variable name="property" select="$properties[1]"/>
		<xsl:variable name="resource">
			<xsl:if test="fn:contains($property/@rdf:resource, '#')"><xsl:value-of select="fn:substring-after($property/@rdf:resource, '#')"/></xsl:if>
			<xsl:if test="fn:not(fn:contains($property/@rdf:resource, '#'))"><xsl:value-of select="$property/@rdf:resource"/></xsl:if>
		</xsl:variable>
		<xsl:choose>
			<xsl:when test="fn:not($properties)"/>
			<xsl:when test="fn:substring-after($resource, '.') != '' ">
				<xsl:value-of select="fn:concat($padd, fn:substring-after($resource, '.'))"/>
				<xsl:call-template name="populate_property_values">
					<xsl:with-param name="properties" select="$properties[fn:position() != 1]"/>
					<xsl:with-param name="mrid2index" select="$mrid2index"/>
					<xsl:with-param name="padd" select=" ' ' "/>
				</xsl:call-template>
			</xsl:when>
			<xsl:when test="$resource !=''">
				<xsl:variable name="key" select="fn:key('mrid', $resource, $mrid2index)"/>
				<xsl:variable name="pos" select="$key[1]/@pos"/>
				<xsl:if test="fn:count($key) = 1 and $pos > 0">
					<xsl:value-of select="fn:concat($padd, '//@DataSetMember.', $pos)"/>
				</xsl:if>
				<xsl:call-template name="populate_property_values">
					<xsl:with-param name="properties" select="$properties[fn:position() != 1]"/>
					<xsl:with-param name="mrid2index" select="$mrid2index"/>
					<xsl:with-param name="padd" select=" ' ' "/>
				</xsl:call-template>
			</xsl:when>
			<xsl:when test="$property !='' ">
				<xsl:value-of select="fn:concat($padd, $property)"/>
				<xsl:call-template name="populate_property_values">
					<xsl:with-param name="properties" select="$properties[fn:position() != 1]"/>
					<xsl:with-param name="mrid2index" select="$mrid2index"/>
					<xsl:with-param name="padd" select=" ' ' "/>
				</xsl:call-template>
			</xsl:when>
		</xsl:choose>
	</xsl:template>
	
</xsl:stylesheet>
