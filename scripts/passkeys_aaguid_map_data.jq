# AAGUID Data Processor for Passkeys
#
# This jq script processes AAGUID (Authenticator Attestation GUID) data from two sources:
# 1. Community-driven passkey authenticator database
# 2. Official FIDO Alliance Metadata Service (MDS)
#
# The script merges data from both sources, with official FIDO MDS data taking precedence
# over community data when conflicts occur. It extracts authenticator descriptions and
# icons to create a unified AAGUID mapping file for passkey authentication.
#
# Input: JSON array with two elements:
#   [0] - Community-driven AAGUID data
#   [1] - Official FIDO MDS data
#
# Output: Merged JSON object mapping AAGUIDs to their descriptions and icons
#
# Used by: passkeys_make_aaguid_datafile.sh

def has_content: . and (. | length > 0);

.[0] as $community_list | .[1] as $official_list |

[
  # Extract AAGUIDs from community-driven list
  ($community_list | to_entries | map(
    select(.value.name | has_content) | 
    {(.key): {
      description: .value.name,
      icon: .value.icon_light
    }}
  )),
  
  # Extract AAGUIDs from official FIDO MDS (overwrites community data)
  ($official_list.entries | map(
    select(.aaguid | has_content) |
    select(.metadataStatement.description | has_content) |
    {(.aaguid): {description: .metadataStatement.description, icon: .metadataStatement.icon}}
  ))
] | flatten | add // {}
