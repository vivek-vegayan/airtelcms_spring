-- Follow-up to 2026-07-28_crq_review_domain1_seed.sql. Rescheduling any of the
-- 5 seeded CRQs (128-132), and separately the pre-existing test CRQ 11, failed
-- with "Could not resolve activity/team for Activity_epoch = RESCH-XXXXXXXX"
-- from CRQ_SP_RESOLVE_REQUEST_Reschedule.
--
-- Root cause: that resolver matches CRQ_ACTIVITY_REQUEST_TBL's
-- (Domain, Layer, Plan_Type, Change_Impact) against ACTIVITY_PLAN_MASTER_TBL
-- exactly. That catalogue uses the scheduling engine's own domain vocabulary
-- (e.g. domain='CEN', layer='ACC', plan_type='Location Shifting',
-- change_impact='NSA'/'SA') and its own chm_domain/chm_sub_domain numbering -
-- both completely independent of ORG_DOMAIN/ORG_SUB_DOMAIN and
-- CRQ_MASTER_TBL.domain_id/sub_domain_id (which only drive Get_CRQ_Review_Details
-- and stay untouched by this file). The seed's task rows used made-up business
-- domain/plan_type values ("Embedded Support" / "Site Migration" / etc.) that
-- don't exist in ACTIVITY_PLAN_MASTER_TBL, so resolution always failed; CRQ 11
-- (pre-existing test data, not part of the seed) had the same problem with its
-- own made-up values ("MPLS-IP" / "OPERATIONS,IMPLEMENTATION").
--
-- Fix: point CRQ_TASK_TBL.domain/node_type/task_profile_type/change_impact at
-- a real, active catalogue entry (domain=CEN, layer=ACC, change_impact=NSA;
-- CRQ 11 -> plan_type 'New Node addition' matching its 'new_equipment_activity'
-- task, CRQs 128-132 -> 'Location Shifting' matching the Site-shifting theme).
-- Also patches the CRQ_ACTIVITY_REQUEST_TBL rows already created by earlier
-- reschedule attempts (RESCH-00000003 for CRQ 11, RESCH-00000021 for CRQ 128) -
-- those are point-in-time snapshots taken at INITIATE, not live joins, so a
-- CRQ_TASK_TBL fix alone does not retroactively fix an attempt already in
-- flight. Verified live: both epochs now return status='success' from
-- Get_Predicted_SlotDates_Reschedule (previously an error_message row).
--
-- Known separate limitation (not fixed here): the resolved team's roster
-- (chm_domain=2/chm_sub_domain=6, team_id=1) only extends a day or two past
-- today, so the calendar step reports "No selectable dates: roster does not
-- extend into the window." This is a roster data-coverage gap, the same class
-- of issue noted for the CRQ 31 fixture in db/migration/2026-07-28_crq_reschedule_wizard.sql's
-- commit notes - not the resolver bug this file fixes.

UPDATE CRQ_TASK_TBL
   SET domain='CEN', node_type='ACC', task_profile_type='Location Shifting', change_impact='NSA'
 WHERE crq_id BETWEEN 128 AND 132;

UPDATE CRQ_ACTIVITY_REQUEST_TBL
   SET Domain='CEN', Plan_Domain='CEN', Layer='ACC', Plan_Type='Location Shifting', Change_Impact='NSA'
 WHERE Activity_Epoch='RESCH-00000021';

UPDATE CRQ_TASK_TBL
   SET domain='CEN', node_type='ACC', task_profile_type='New Node addition', change_impact='NSA'
 WHERE crq_id=11;

UPDATE CRQ_ACTIVITY_REQUEST_TBL
   SET Domain='CEN', Plan_Domain='CEN', Layer='ACC', Plan_Type='New Node addition', Change_Impact='NSA'
 WHERE Activity_Epoch='RESCH-00000003';

-- Second pass, same day: resolution succeeded but the calendar still came back
-- empty ("No selectable dates ... roster does not extend into the window").
-- Root cause: CRQ_SP_BUILD_POOL requires ACTIVITY_TASK_EMPLOYEE_TBL.network_execution='YES'
-- AND CRQ_FN_VENDOR_MATCH(device_vendor_capability, p_vendor)=1. Under chm_domain=2/
-- chm_sub_domain=6, exactly ONE user has network_execution='YES' - RESCHTEST01
-- (user_id 403), whose device_vendor_capability is 'Cisco' only. Every vendor
-- used above (Ericsson/Nokia/Vertiv/Huawei/ECI) failed that match, so tmp_pool
-- was empty -> MAX(shift_date) NULL -> the "roster does not extend" message,
-- which is really "no candidate engineer at all", not a narrow roster window.
UPDATE CRQ_TASK_TBL SET vendor='Cisco' WHERE crq_id BETWEEN 128 AND 132;
UPDATE CRQ_TASK_TBL SET vendor='Cisco' WHERE crq_id=11;
UPDATE CRQ_ACTIVITY_REQUEST_TBL SET Vendor='Cisco' WHERE Activity_Epoch IN ('RESCH-00000021','RESCH-00000003');
-- Verified live: both epochs now return status='success', startDate=2026-07-29,
-- endDate=2026-08-05 (RESCHTEST01's roster ceiling), with one selectable day.
