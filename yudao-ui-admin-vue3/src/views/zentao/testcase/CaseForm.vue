<template>
  <!-- 测试用例新建/编辑。步骤编辑器是重点：支持「步骤组 + 组内步骤」两层 -->
  <el-dialog v-model="visible" :title="isEdit ? '编辑用例' : '新建用例'" width="860px" append-to-body @closed="reset">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-10px"
      title="只有「步骤」变化才会产生新版本"
      description="标题、前置条件、优先级、状态都是原地修改，版本号不变；一旦步骤变了，版本号 +1 并且状态会被打回「待评审」。"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="所属产品" prop="product">
            <el-select v-model="form.product" class="w-full" filterable @change="onProductChange">
              <el-option v-for="p in productList" :key="p.id" :label="p.name" :value="p.id!" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="分支/平台">
            <BranchSelect v-model="form.branch" :product="form.product" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="所属模块">
            <ModuleSelect v-model="form.module" :root="form.product" type="case" :branch="form.branch" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" placeholder="请输入用例标题" maxlength="255" />
      </el-form-item>

      <el-form-item label="关联需求">
        <el-select v-model="form.story" class="w-full" clearable filterable placeholder="不关联需求">
          <el-option v-for="s in storyList" :key="s.id" :label="`#${s.id} ${s.title}`" :value="s.id!" />
        </el-select>
        <div class="text-12px text-gray-400">
          关联时会把需求当前版本冻结下来；需求以后升版，这里会提示「待确认」，需要手动确认
        </div>
      </el-form-item>

      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="类型" prop="type">
            <el-select v-model="form.type" class="w-full">
              <el-option v-for="o in CASE_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="优先级">
            <el-select v-model="form.pri" class="w-full">
              <el-option v-for="o in CASE_PRI_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="状态">
            <el-select v-model="form.status" class="w-full">
              <el-option v-for="o in CASE_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="测试环节">
        <el-select v-model="stageList" class="w-full" multiple collapse-tags placeholder="可多选">
          <el-option v-for="o in CASE_STAGE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>

      <el-form-item label="前置条件">
        <el-input v-model="form.precondition" type="textarea" :rows="2" placeholder="执行这条用例前需要满足的条件" />
      </el-form-item>

      <el-form-item label="关键词">
        <el-input v-model="form.keywords" placeholder="逗号分隔，用于检索" maxlength="255" />
      </el-form-item>

      <el-form-item label="步骤">
        <div class="w-full">
          <div v-for="(step, index) in steps" :key="index" class="step-row">
            <el-tag :type="step.type === 'group' ? 'warning' : 'info'" class="step-index">
              {{ step.type === 'group' ? '组' : index + 1 }}
            </el-tag>
            <el-input
              v-model="step.desc"
              :placeholder="step.type === 'group' ? '步骤组名称' : '步骤描述'"
              class="step-desc"
            />
            <el-input
              v-if="step.type !== 'group'"
              v-model="step.expect"
              placeholder="预期结果"
              class="step-expect"
            />
            <el-button link type="danger" @click="removeStep(index)">删除</el-button>
            <el-button link type="primary" @click="insertStepAfter(index)">下方插入</el-button>
          </div>

          <div class="mt-8px">
            <el-button size="small" @click="addStep"><Icon icon="ep:plus" class="mr-5px" />添加步骤</el-button>
            <el-button size="small" @click="addGroup"><Icon icon="ep:folder-add" class="mr-5px" />添加步骤组</el-button>
            <span class="text-12px text-gray-400 ml-8px">
              步骤组用来分组展示（编号会算成 1. / 1.1）；组内步骤的层级由顺序决定，最多两层
            </span>
          </div>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import * as CaseApi from '@/api/zentao/testcase'
import * as ProductApi from '@/api/zentao/product'
import * as StoryApi from '@/api/zentao/story'
import BranchSelect from '../components/BranchSelect.vue'
import ModuleSelect from '../components/ModuleSelect.vue'
import {
  CASE_TYPE_OPTIONS,
  CASE_STAGE_OPTIONS,
  CASE_STATUS_OPTIONS,
  CASE_PRI_OPTIONS
} from './constants'

defineOptions({ name: 'ZentaoCaseForm' })

const emit = defineEmits(['success'])
const message = useMessage()

const visible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const formRef = ref()
const productList = ref<any[]>([])
const storyList = ref<any[]>([])
const stageList = ref<string[]>([])
const steps = ref<CaseApi.CaseStepVO[]>([])

const form = ref<CaseApi.CaseVO>({
  id: undefined,
  product: undefined,
  branch: 0,
  module: undefined,
  story: undefined,
  title: '',
  precondition: '',
  keywords: '',
  pri: 3,
  type: 'feature',
  status: 'normal'
})

const rules = {
  product: [{ required: true, message: '请选择所属产品', trigger: 'change' }],
  title: [{ required: true, message: '请输入用例标题', trigger: 'blur' }],
  type: [{ required: true, message: '请选择用例类型', trigger: 'change' }]
}

const onProductChange = async (product: number) => {
  form.value.branch = 0
  form.value.module = undefined
  form.value.story = undefined
  // 需求下拉：只要同产品下的需求，分页接口取前 200 条足够
  const page = product ? await StoryApi.getStoryPage({ product, pageNo: 1, pageSize: 200 } as any) : null
  storyList.value = page?.list ?? []
}

const addStep = () => steps.value.push({ type: 'step', desc: '', expect: '' })
const addGroup = () => steps.value.push({ type: 'group', desc: '', expect: '' })
const removeStep = (index: number) => steps.value.splice(index, 1)
const insertStepAfter = (index: number) => steps.value.splice(index + 1, 0, { type: 'step', desc: '', expect: '' })

/**
 * 把界面上的扁平步骤转成后端约定：parent = 父步骤组在数组里的下标。
 *
 * 界面只支持两层（组 + 组内步骤），所以规则很直接：
 * 每遇到一个「步骤组」，它后面的步骤就归到它名下，直到出现下一个组。
 */
const buildSteps = (): CaseApi.CaseStepVO[] => {
  const result: CaseApi.CaseStepVO[] = []
  let currentGroup = -1
  for (const raw of steps.value) {
    if (!raw.desc?.trim()) continue
    if (raw.type === 'group') {
      currentGroup = result.length
      result.push({ type: 'group', desc: raw.desc.trim(), expect: '' })
    } else {
      result.push({
        type: 'step',
        desc: raw.desc.trim(),
        expect: raw.expect ?? '',
        // 顶层步骤传 -1（0 是合法的「第一个组」下标，不能当顶层用）
        parent: currentGroup
      })
    }
  }
  return result
}

const open = async (options: { type: 'create' | 'edit'; row?: CaseApi.CaseVO; product?: number }) => {
  visible.value = true
  isEdit.value = options.type === 'edit'
  productList.value = await ProductApi.getProductSimpleList()

  if (options.type === 'edit' && options.row) {
    const detail = await CaseApi.getCase(options.row.id!, 0)
    form.value = { ...detail, status: detail.status ?? 'normal' }
    stageList.value = (detail.stage ?? '').split(',').filter(Boolean)
    steps.value = (detail.steps ?? []).map((s) => ({ ...s }))
    if (form.value.product) {
      const page = await StoryApi.getStoryPage({ product: form.value.product, pageNo: 1, pageSize: 200 } as any)
      storyList.value = page?.list ?? []
    }
  } else {
    form.value = {
      product: options.product,
      branch: 0,
      module: undefined,
      story: undefined,
      title: '',
      precondition: '',
      keywords: '',
      pri: 3,
      type: 'feature',
      status: 'normal'
    }
    stageList.value = []
    steps.value = [{ type: 'step', desc: '', expect: '' }]
    if (options.product) await onProductChange(options.product)
  }
}
defineExpose({ open })

const reset = () => formRef.value?.resetFields()

const handleSubmit = async () => {
  await formRef.value.validate()
  const payload: CaseApi.CaseVO = {
    ...form.value,
    stage: stageList.value.join(','),
    steps: buildSteps()
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await CaseApi.updateCase(payload)
      message.success('修改成功（若步骤有变化，版本号 +1 并回到待评审）')
    } else {
      await CaseApi.createCase(payload)
      message.success('新建成功')
    }
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.step-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.step-index {
  width: 44px;
  text-align: center;
  flex: none;
}
.step-desc {
  flex: 1 1 40%;
}
.step-expect {
  flex: 1 1 40%;
}
</style>
