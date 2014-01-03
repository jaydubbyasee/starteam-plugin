/**
 *
 */
package hudson.plugins.starteam;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.starteam.Label;
import com.starteam.PromotionState;
import com.starteam.View;
import com.starteam.ViewConfiguration;
import com.starteam.util.DateTime;
//import com.starbase.util.OLEDate;

/**
 * 
 * Contents of a View can be adjusted by selecting versions other then the tip. 
 * 
 * @author Jan Ruzicka
 *
 */
public class StarTeamViewSelector implements Serializable {
	private static final long serialVersionUID = 1L;

	private final static Pattern labelPattern = Pattern.compile("%\\{(.*?):BUILD_NUMBER\\}");
	
	/**
	 * Current: view brings the most recent or "tip" versions of items, 
	 * Label: view brings items with the specified label attached, 
	 * Time: view brings items designated by time,
	 * Promotion: view brings items designated by a promotion state, similar to label-based view.
	 */
	enum ConfigType {
		CURRENT, LABEL, TIME, PROMOTION
	};

	private final String configInfo;       // configuration information Label name, Promotion State name or date 
	private final ConfigType configType;   // type of configuration.
	private final DateFormat df = new SimpleDateFormat("yyyy/M/d HH:mm:ss");

	/**
	 * Default constructor
	 *
	 * @param configType 
	 * Current: view brings the most recent or "tip" versions of items, 
	 * Label: view brings items with the specified label attached, 
	 * Time: view brings items designated by time,
	 * Promotion: view brings items designated by a promotion state, similar to label-based view.
	 * @param configInfo 
	 * Information according to configuration type. Label name, Promotion State name or date 
	 * 
	 * @throws ParseException 
	 */
	public StarTeamViewSelector(String configInfo, String configType) throws ParseException {
		this.configInfo = configInfo;
		ConfigType result = ConfigType.CURRENT;
		if (configType != null)	{
			try{
				result = ConfigType.valueOf(configType.toUpperCase());
			} catch ( IllegalArgumentException ignored)	{
				// ignored exception - by default the type will go to CURRENT
			}
		}
		this.configType = result;

	    if (this.configType == ConfigType.TIME) {
	    	df.parse(configInfo);
	    }
	}
	
	public StarTeamViewSelector(Date time) {
		if (time != null)	{
			this.configType = ConfigType.TIME;
			this.configInfo = df.format(time);
		} else {
			this.configType = ConfigType.CURRENT;
			this.configInfo = null;
	    }
	}

	public View configView(View baseView, int buildNumber) throws StarTeamSCMException, ParseException{
		final ViewConfiguration configuration;

		if (configInfo != null && !configInfo.isEmpty()) {
			switch (configType) {
			case CURRENT:
				configuration = ViewConfiguration.createTip();
				break;
			case LABEL:
				Label label;
				// check if label is a pattern
				String labelName = expandLabelPattern(configInfo, buildNumber);
				if (!configInfo.equals(labelName)) {
					label = createLabelInView(baseView, labelName, buildNumber);
				} else {
					label = findLabelInView(baseView, labelName);
				}
				//configuration = ViewConfiguration.createFromLabel(labelId);
                                configuration = ViewConfiguration.createFrom(label);
				break;
			case PROMOTION:		          
				// note: If the promotion state is assigned to <<current>> then the resulting ID will be NULL and
				// we will revert to a view based on the current tip.
				PromotionState promotionState = findPromotionStateInView(baseView, configInfo);
				if (promotionState != null) {
                                    configuration = ViewConfiguration.createFrom(promotionState);
                                    //configuration = ViewConfiguration.createFromPromotionState(promotionStateId);
				} else {					
					configuration = ViewConfiguration.createTip();
				}
				break;
			case TIME:
	            Date effectiveDate = df.parse(configInfo);
                                configuration = ViewConfiguration.createFrom(new DateTime(effectiveDate));
				break;
			default:
				throw new StarTeamSCMException("Could not construct view - no configuration provided");
			}
		} else {
			configuration = ViewConfiguration.createTip();
		}
		return new View(baseView, configuration);
	}

	public static String expandLabelPattern(final String labelformat, final int buildNumber) {
		Matcher m = labelPattern.matcher(labelformat);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String fmt = "%" + m.group(1);
			m.appendReplacement(sb, String.format(fmt, buildNumber));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private static Label findLabelInView(final View view, final String labelname) throws StarTeamSCMException {
		for (Label label : view.getLabels()) {
			if (labelname.equals(label.getName())) {
				return label;
			}
		}
		throw new StarTeamSCMException("Couldn't find label [" + labelname + "] in view " + view.getName());
	}

	private static Label createLabelInView(final View view, final String labelName, final int buildNumber) throws StarTeamSCMException {
		final String labelDesc = String.format("Jenkins build %d", buildNumber);
		final boolean buildLabel = true;
		final boolean frozen = true;
                Label label = view.createViewLabel(labelName, labelDesc, DateTime.now(), buildLabel, frozen);
		return label;
	}

	private static PromotionState findPromotionStateInView(final View view, final String promotionState) throws StarTeamSCMException {
		for (PromotionState ps : view.getPromotionModel().getPromotionStates()) {
			if (promotionState.equals(ps.getName())) {
				if (ps.getLabel().getID() == -1) {
					// PROMOTION STATE is set to <<current>>
					return null;
				}
				return ps;
			}
		}
		throw new StarTeamSCMException("Couldn't find promotion state " + promotionState + " in view " + view.getName());
	}

	public String getConfigInfo() {
		return configInfo;
	}

	public String getConfigType() {
		return configType.name();
	}

}
